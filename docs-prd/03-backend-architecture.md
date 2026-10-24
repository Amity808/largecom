# 03 — Backend Architecture (`largecom`)

## 1. Current state of `largecom`

| File | Content | Action |
|:--|:--|:--|
| `pom.xml` | Boot parent **4.1.1**, Java **25**, `spring-boot-starter`, `spring-boot-starter-web` pinned to **4.1.0** | Remove the `<version>` on the web starter so the parent manages it. Add the dependencies below. |
| `LargecomApplication.java` | Standard main | Keep |
| `HomeControler.java` | Empty `@Controller` (misspelled) | Delete it; Week 1 adds a real `HealthController` / actuator |
| `application.properties` | Name + port 8080 | Replace with the safe config in [07](./07-database-safety.md) |

Package: `org.ecom.largecom`. Keep it.

## 2. Stack

| Concern | Choice | Notes |
|:--|:--|:--|
| Runtime | Java 25, Spring Boot 4.1 | Enable virtual threads: `spring.threads.virtual.enabled=true` |
| Web | Spring MVC | Boot 4 name: `spring-boot-starter-webmvc` (`-web` still works, but is deprecated) |
| Persistence | Spring Data JPA (Hibernate 7) + PostgreSQL 16 | `ddl-auto=validate` |
| Migrations | Flyway | `spring-boot-starter-flyway` + `flyway-database-postgresql` |
| Validation | `spring-boot-starter-validation` | `@Valid` on every request DTO |
| Security | Spring Security + `spring-boot-starter-oauth2-resource-server` | We issue our own HS256 JWTs (Nimbus `JwtEncoder` / `JwtDecoder`) |
| Passwords | `Argon2PasswordEncoder` | Needs `org.bouncycastle:bcprov-jdk18on` |
| API docs | springdoc-openapi (version line for Boot 4) | `/swagger-ui.html`; the frontend can generate types from `/v3/api-docs` |
| Ops | `spring-boot-starter-actuator` | `/actuator/health` |
| Tests | `spring-boot-starter-test`, `spring-boot-testcontainers`, Testcontainers PostgreSQL, `spring-security-test` | Never the real DB |
| Files | `FileStorage` interface: `LocalFileStorage` (dev), `S3FileStorage` (R2, prod) | AWS SDK v2 S3 client for R2 |
| Payments | Paystack REST over `RestClient` | Week 9 |
| Email | Resend REST or `spring-boot-starter-mail` | Week 10 |
| Cache / rate limit | Start with in-process (Caffeine, Bucket4j); move to Redis only when you run more than one instance | Week 12 |

> **Tip:** generate the dependency block once at start.spring.io (Boot 4.1, Java 25, Maven) with: Spring Web, Spring Data JPA, PostgreSQL Driver, Flyway Migration, Validation, Spring Security, OAuth2 Resource Server, Actuator, Testcontainers. Then copy only the `<dependencies>` into the existing `pom.xml`. This avoids guessing Boot 4 artifact names.

## 3. Package layout: modular monolith, organized by feature

```
org.ecom.largecom
├── LargecomApplication.java
├── common/
│   ├── api/            ApiResponse<T>, PageResponse<T>, ErrorResponse, GlobalExceptionHandler
│   ├── error/          AppException, ErrorCode (VALIDATION_ERROR, NOT_FOUND, CONFLICT, INSUFFICIENT_STOCK…)
│   ├── jpa/            BaseEntity (id, createdAt, updatedAt), TenantScopedEntity (@TenantId tenantId)
│   ├── money/          Kobo helpers
│   ├── slug/           Slugifier (same rules as web/src/lib/slugify.ts)
│   ├── idempotency/    IdempotencyService + table
│   └── config/         SchemaSafetyGuard, JacksonConfig, OpenApiConfig, CorsConfig
├── tenancy/            Tenant, TenantContext, TenantResolverFilter (host/slug), CurrentTenantResolver (Hibernate)
├── auth/               User, Membership, RefreshToken, AuthController, JwtService, CookieService, SecurityConfig
├── settings/           StoreSettings (in tenants.settings jsonb), SettingsController
├── theme/              StoreTheme (jsonb), ThemeController, TemplateService (loads /templates/*.json)
├── onboarding/         OnboardingService (template → theme + catalog + portfolio)
├── catalog/            Category, Product, ProductVariant, ProductController, CategoryController
├── media/              FileStorage, UploadController
├── inventory/          Location, InventoryLevel, InventoryMovement, InventoryService
├── customers/          Customer, CustomerController
├── orders/             Order, OrderItem, OrderNumberService, OrderService, OrderController
├── pos/                RegisterSession, PosSettings, PosCheckoutService, PosController
├── portfolio/          PortfolioItem, ServicePackage, BookingInquiry + controllers
├── storefront/         Public read controllers, ContactMessage, NewsletterSubscriber, Cart
├── payments/           Payment, PaystackClient, PaystackWebhookController
├── wallet/             LedgerEntry, BankAccount, PayoutRequest, WalletService
├── expenses/           Expense
├── deliveries/         Delivery
├── messaging/          Campaign, MessageSender (Email/Sms/WhatsApp impls), CampaignJob
├── team/               Invite, TeamController
├── domains/            CustomDomain, DnsVerifier
└── analytics/          AnalyticsService (SQL queries), DashboardController
```

Inside each feature folder use the same small set of files: `Entity`, `Repository`, `Service`, `Controller`, `dto/` (Java `record`s). Controllers never touch repositories directly.

**Module rule:** a feature calls another feature only through its `Service`, never its repository. For example, `orders` calls `InventoryService.reserve(...)`, not `InventoryLevelRepository`.

## 4. Multi-tenancy

### 4.1 Model
- `tenants` = one store. `users` are **global** (email is unique platform-wide). `memberships(user, tenant, role)` link them.
- Today one user = one store. Memberships make team invites (Week 10) possible without a migration rewrite.
- Every business table has `tenant_id UUID NOT NULL`.

### 4.2 Resolving the tenant for a request

| Request type | Tenant comes from |
|:--|:--|
| Dashboard API (`/api/v1/**` except storefront, auth, webhooks) | JWT claim `tid` (active tenant) |
| Storefront API (`/api/v1/storefront/**`) | `X-Store-Slug` header (set by the Next.js server), else `Host` (`acme.punta.shop` → `acme`; or lookup in `custom_domains` for verified domains) |
| Auth, webhooks, templates | No tenant. A sentinel "no tenant" id means tenant tables return nothing. |

`TenantResolverFilter` runs once per request, after authentication. It puts the id into `TenantContext` and clears it in `finally`.

```java
public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    public static final UUID NONE = new UUID(0, 0);
    public static UUID get() { var id = CURRENT.get(); return id == null ? NONE : id; }
    static void set(UUID id) { CURRENT.set(id); }
    static void clear() { CURRENT.remove(); }
}
```

(`ThreadLocal` works with virtual threads. Java 25's `ScopedValue` is a cleaner alternative once you're comfortable.)

### 4.3 Enforcing isolation: two layers

**Layer 1 (Week 2): Hibernate `@TenantId`.**

```java
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
}

@Component
class CurrentTenantResolver implements CurrentTenantIdentifierResolver<UUID> {
    public UUID resolveCurrentTenantIdentifier() { return TenantContext.get(); }
    public boolean validateExistingCurrentSessions() { return true; }
}
// Register it with a HibernatePropertiesCustomizer:
// props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver)
```

What this does:
- Hibernate adds `where tenant_id = ?` to every entity query.
- It sets `tenant_id` automatically on insert.
- It refuses to load another tenant's row.

**Caveat:** **native SQL is not filtered.** Every `@Query(nativeQuery = true)` and every `JdbcTemplate` query (analytics, stock decrement) must include `tenant_id = :tenantId` explicitly. Code review checks this.

**Layer 2 (Week 12): Postgres Row-Level Security** as a safety net. The policies are in `docs/PUNTA/architecture/03-multi-tenancy.md`. The app sets `SELECT set_config('app.current_tenant', ?, true)` at the start of each transaction, via a `TransactionSynchronization` or a custom `DataSource` wrapper. Requires the app to connect as a non-owner role ([07](./07-database-safety.md#database-roles-week-12-recommended-from-day-one)).

### 4.4 Mandatory test per module

```java
@Test void tenantBCannotSeeTenantA() {
    var a = registerTenant("a@x.com"); var b = registerTenant("b@x.com");
    var productId = as(a).post("/api/v1/products", newProduct()).id();
    as(b).get("/api/v1/products").assertEmpty();
    as(b).get("/api/v1/products/" + productId).assertStatus(404);
}
```

## 5. Authentication

### 5.1 Tokens and cookies

| Token | Lifetime | Where | Contents |
|:--|:--|:--|:--|
| Access (JWT HS256) | 15 min | httpOnly cookie **`punta_session`**, `Path=/`, `SameSite=Lax`, `Secure` in prod, `Max-Age` 7 days | `sub`=userId, `tid`=tenantId, `role`, `exp` |
| Refresh (random 256-bit, stored **hashed**) | 7 days, rotated on every use | httpOnly cookie `punta_refresh`, `Path=/api/v1/auth` | — |

The access cookie is **named `punta_session` on purpose**, because `web/src/proxy.ts` already checks for that cookie. The cookie lives for 7 days, while the JWT inside expires in 15 minutes:
- The guard keeps working.
- The API returns `401 TOKEN_EXPIRED`.
- The frontend calls `POST /auth/refresh` once and retries the request.

Cookies only work if the browser sees the API on the **same origin** as the app. In dev, Next.js rewrites `/api/*` → `http://localhost:8080/api/*` ([06](./06-frontend-integration.md)).

### 5.2 Spring Security setup
- Stateless (`SessionCreationPolicy.STATELESS`).
- `oauth2ResourceServer().jwt()` with a custom `BearerTokenResolver` that reads the `punta_session` cookie (and also the `Authorization` header, for Swagger and tests).
- A `JwtAuthenticationConverter` maps `role` to authorities.
- **CSRF:** cookie auth needs protection. Use all three:
  - `SameSite=Lax` cookies, so browsers don't send them on cross-site POSTs.
  - Only accept `application/json` bodies on mutating requests.
  - An `OriginCheckFilter` that rejects mutating requests whose `Origin` isn't the app origin.
  - Spring's own CSRF stays disabled, with this justification written in `SecurityConfig`.
- Public: `/api/v1/auth/register|login|refresh|logout`, `/api/v1/storefront/**`, `/api/v1/webhooks/**`, `/api/v1/templates`, `/actuator/health`, Swagger.

### 5.3 Authorization

| Role | Frontend label | Can |
|:--|:--|:--|
| `OWNER` | Owner | Everything, including wallet, payouts and team |
| `MANAGER` | Manager | Everything except wallet payouts, bank account and team |
| `STAFF` | Fulfillment Staff | Orders (view, update status), POS, deliveries, customers (view), inventory adjust |

Enforce with `@PreAuthorize("hasAnyRole('OWNER','MANAGER')")` on service methods.

## 6. API conventions

| Rule | Value |
|:--|:--|
| Base path | `/api/v1` |
| JSON casing | **camelCase**, identical to the frontend TS types |
| IDs | UUID strings |
| Money | `long` kobo, field names end in `Kobo` (`priceKobo`) |
| Dates | ISO-8601 UTC strings (`2026-10-16T09:30:00Z`); dates without time as `YYYY-MM-DD` |
| Success body | `{ "data": … }` |
| List body | `{ "data": [...], "page": { "number": 0, "size": 50, "totalElements": 132, "totalPages": 3 } }` |
| Paging | `?page=0&size=50` (default 50, max 200). The frontend filters client-side today, so start simple. |
| Errors | `{ "error": { "code": "VALIDATION_ERROR", "message": "...", "details": [{ "field": "priceKobo", "message": "must be ≥ 0" }] } }` |
| Idempotency | `Idempotency-Key` header required on `POST /pos/checkout`, `POST /storefront/checkout`, `POST /wallet/payouts` |

Error codes: `VALIDATION_ERROR` 400, `UNAUTHORIZED` 401, `TOKEN_EXPIRED` 401, `FORBIDDEN` 403, `NOT_FOUND` 404, `CONFLICT` 409, `INSUFFICIENT_STOCK` 409, `VERSION_CONFLICT` 409, `RATE_LIMITED` 429, `INTERNAL_ERROR` 500.

## 7. Key implementation patterns

### Per-tenant order numbers
```sql
-- tenant_counters(tenant_id, name, value)
UPDATE tenant_counters SET value = value + 1
WHERE tenant_id = :tid AND name = 'order' RETURNING value;   -- → ORD-{value}, starts at 1000
```
Row lock = no duplicates, even under concurrent POS checkouts.

### No-oversell stock decrement
```sql
UPDATE inventory_levels SET on_hand = on_hand - :qty, updated_at = now()
WHERE tenant_id = :tid AND product_id = :pid AND variant_id IS NOT DISTINCT FROM :vid
  AND location_id = :loc AND on_hand - reserved >= :qty;
-- 0 rows updated → throw INSUFFICIENT_STOCK (whole transaction rolls back)
```

### Idempotency
`idempotency_keys(tenant_id, key, request_hash, response_json, created_at)`:
1. Insert the key at the start of the transaction.
2. On a unique conflict, return the stored response.
3. If the hash differs, return `409`.

### Wallet = ledger
Never `UPDATE wallet SET balance = …`. Insert `ledger_entries` (signed `amountKobo`, `type`, `status`, `availableAt`). Balances:
```sql
available = SUM(amount) WHERE status='completed' AND available_at <= now()
pending   = SUM(amount) WHERE status='completed' AND available_at > now()   -- T+1 settlements
```

### Theme as JSON
`store_themes.document jsonb` holds exactly the frontend's theme object. Map it with Hibernate's `@JdbcTypeCode(SqlTypes.JSON)` onto a Java record tree, or onto Jackson's `JsonNode` if you want to stay flexible at first. Validate the section `type` values in the service.

## 8. Configuration and secrets

| Env var | Used for |
|:--|:--|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | App connection |
| `FLYWAY_USER`, `FLYWAY_PASSWORD` | Migration connection (Week 12) |
| `JWT_SECRET` | ≥ 32 random bytes, base64 |
| `APP_ORIGIN` | `http://localhost:3000` / `https://app.punta.shop` (CORS + Origin check) |
| `STOREFRONT_ROOT_DOMAIN` | `localhost` (dev) / `punta.shop` |
| `STORAGE_*` | R2 bucket, keys, public URL |
| `PAYSTACK_SECRET_KEY` | Payments + webhook signature |
| `RESEND_API_KEY` | Email |

Put local values in `largecom/.env` (git-ignored) and load them with your IDE run config or `set -a; source .env`. Never commit them.
