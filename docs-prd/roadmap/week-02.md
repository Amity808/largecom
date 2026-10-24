# Week 2 — Auth & Multi-Tenancy

**Goal:** A merchant can register, log in, refresh, log out and call `/me`. Every later table is automatically tenant-scoped. `/register` and `/login` in `/web` use the real API.

**Read first:** [03 §4–5](../03-backend-architecture.md#4-multi-tenancy), [05 Auth](../05-api-contract.md#auth--week-2)

## Day 1 — Schema + entities
- [ ] `V2__tenants_users_memberships.sql`, `V3__refresh_tokens.sql`, `V4__tenant_counters_idempotency.sql` ([04](../04-data-model.md)).
- [ ] Entities: `Tenant`, `User`, `Membership`, `RefreshToken` (global, **no** `@TenantId`).
- [ ] `TenantScopedEntity` with `@TenantId`, plus `CurrentTenantResolver` registered through `HibernatePropertiesCustomizer`.
- [ ] `TenantContext` (`ThreadLocal`, `NONE` sentinel).

## Day 2 — Registration
- [ ] `PasswordEncoder` bean: `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` (add bouncycastle).
- [ ] `AuthService.register(storeName, email, password)` in **one** transaction:
  1. Validate: email format, password ≥ 8, store name 2–60 chars.
  2. Email already used → `409 CONFLICT` "An account with this email already exists."
  3. Slug = `slugify(storeName)`. If it's taken or reserved, append `-2`, `-3`, …
  4. Insert the tenant, the user, an OWNER membership, and `tenant_counters('order', 1000)`.
  5. Default store settings JSON: `{tagline:"", supportEmail: email, supportPhone:"", vatPercent:7.5, notifyOnNewOrder:true, autoFulfillDigital:false}`.
- [ ] The default location "Main Store" is added in Week 4. Leave a `// TODO week 4` in `register`.

## Day 3 — Tokens + cookies
- [ ] `JwtService`: HS256 with a `JWT_SECRET` key (Nimbus `JwtEncoder` / `JwtDecoder`). Claims `sub`, `tid`, `role`, `exp` (15 min).
- [ ] Refresh token: 32 random bytes in base64url. Store only `SHA-256(token)`. Rotate on use. Reusing a revoked token revokes the whole chain.
- [ ] `CookieService`:
  - `punta_session` (httpOnly, Lax, Path=/, Max-Age 7d, Secure in prod)
  - `punta_refresh` (Path=/api/v1/auth)
  - `clear()`
- [ ] Endpoints: `register`, `login`, `refresh`, `logout`, `GET /me`.
- [ ] Login failure always returns the same 401 message. Run the hash comparison even when the user isn't found (timing).

## Day 4 — Security config + tenant filter
- [ ] `SecurityConfig`:
  - stateless
  - resource server JWT with a cookie-based `BearerTokenResolver` (falls back to the `Authorization` header)
  - public paths listed in [03 §5.2](../03-backend-architecture.md#52-spring-security-setup)
- [ ] `TenantResolverFilter`: authenticated request → `tid` claim → `TenantContext.set`. Clear it in `finally`.
- [ ] `OriginCheckFilter` for POST/PUT/PATCH/DELETE (allow `APP_ORIGIN`, any storefront host, and missing Origin for server-to-server and tests).
- [ ] 401 responses use the error envelope: `TOKEN_EXPIRED` when the JWT has expired, `UNAUTHORIZED` otherwise.

## Day 5 — Tests + frontend switch
- [ ] Integration tests:
  - register → `/me`
  - duplicate email → 409
  - wrong password → 401
  - refresh rotates the token
  - an old refresh token fails
  - logout clears cookies
- [ ] **Isolation harness:** a dummy `TenantScopedEntity` (test-only migration under `src/test/resources/db/migration`), with a test that tenant B can't read tenant A's rows. Reuse this helper every week.
- [ ] `/web`, following [06 §1, §2, §4](../06-frontend-integration.md):
  - rewrite + API client
  - auth store → API
  - delete `crypto.ts` and `session-cookie.ts`
  - password min 8
  - dashboard layout redirects to `/onboarding` if `!hasOnboarded`
- [ ] Manual check: register in Chrome, log in from Safari with the same account, same user.

## Done when
- [ ] Two accounts can't see each other's `/me` data, even with a swapped cookie (a tampered JWT is rejected).
- [ ] The `punta_session` cookie is httpOnly (DevTools → Application → Cookies).
- [ ] `proxy.ts` redirects still work unchanged.
