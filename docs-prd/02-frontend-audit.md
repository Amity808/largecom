# 02 — Frontend Audit (`/web`)

Snapshot of the Next.js 16 app as of 2026-10-16. Paths are relative to `web/src`.

**Legend:**
- 🟢 works, saved in localStorage
- 🟡 works, in-memory only (resets on reload)
- 🔴 fake or hardcoded

## 1. Where data lives today

| Store | File | Persistence | Becomes |
|:--|:--|:--|:--|
| `useAuthStore` | `stores/auth-store.ts` | localStorage `punta-auth` (global) | `/auth/*`, `/me` |
| `useProductStore` | `stores/product-store.ts` | localStorage `<userId>:punta-products` | `/products`, `/categories` |
| `usePortfolioStore` | `stores/portfolio-store.ts` | localStorage `<userId>:punta-portfolio` | `/portfolio` |
| `useThemeStore` | `stores/theme-store.ts` | localStorage `<userId>:punta-store-theme` | `/theme`, `/settings` (businessMode) |
| `useRegisterStore` | `stores/register-store.ts` | localStorage `<userId>:punta-register` | `/pos/registers` |
| `useDashboardStore` | `lib/mock-dashboard-data.ts` | in memory | orders, customers, inventory, deliveries, campaigns, wallet, expenses, settings, team, domain |
| `usePosDataStore` | `lib/mock-pos-data.ts` | in memory | `/pos/sales`, `/pos/shifts`, `/pos/summary`, `/pos/settings` |
| `usePortfolioDataStore` | `lib/mock-portfolio-data.ts` | in memory | `/bookings`, `/packages` |

Per-user scoping is done by `lib/store/scoped-storage.ts`, which prefixes keys with `currentUserId`. Its comment says it "stands in for per-tenant storage on the real backend". That job moves to the backend.

## 2. Page → API map

### Auth & onboarding

| Route | State | Sends / needs | Endpoint(s) |
|:--|:--|:--|:--|
| `/register` | 🟢 | `storeName`, `email`, `password` (min 6, change to 8) | `POST /auth/register` |
| `/login` | 🟢 | `email`, `password`, `?next=` | `POST /auth/login` |
| logout (user menu) | 🟢 | — | `POST /auth/logout` |
| `proxy.ts` guard | 🔴 checks only that the cookie exists | `punta_session` cookie | Backend sets an httpOnly cookie; the guard stays a presence check, and the API validates it |
| `/onboarding` | 🟢 | `templateId` (or Skip → `boutique`) | `GET /templates`, `POST /onboarding` |
| `/` landing | 🟢 | current user | `GET /me` |

### Dashboard

| Route | State | Reads | Actions → endpoint |
|:--|:--|:--|:--|
| `/dashboard` | 🔴 ecommerce cards hardcoded; 🟡 POS and portfolio cards come from mock stores | stats for each mode | `GET /dashboard/summary?mode=` |
| `/dashboard/products` | 🟢 | products, categories | toggle active / featured → `PATCH /products/{id}`; delete → `DELETE /products/{id}` |
| `/dashboard/products/new`, `/[id]` | 🟢 | product, categories | `POST/PUT /products`; `POST /categories`; image → `POST /uploads` |
| `/dashboard/products/[id]/variants` | 🟢 | variants | `POST/PATCH/DELETE /products/{id}/variants/{vid}` |
| `/dashboard/orders` | 🟡 | orders + KPIs | status → `PATCH /orders/{id}/status`; `GET /orders/summary` |
| `/dashboard/orders/new` | 🟡 | customers, active products | `POST /orders` (the server decrements stock) |
| `/dashboard/pos` | 🟢 register; 🟡 orders | products, customers, open session | `POST /pos/registers` (open), `POST /pos/checkout`, `POST /pos/registers/{id}/close` |
| `/dashboard/pos/history` | 🔴 seed only; range buttons and CSV do nothing | shifts, sales, daily summary | `GET /pos/shifts`, `GET /pos/sales`, `GET /pos/summary?range=` |
| `/dashboard/pos/settings` | 🟡 nothing reads it | receipt settings | `GET/PUT /pos/settings` |
| `/dashboard/customers` | 🟡 no edit or delete | customers + KPIs | `POST /customers`; add `PATCH`/`DELETE` |
| `/dashboard/inventory` | 🟡 **separate from `Product.stock`** | items per location | `POST /inventory/adjust`; "Add item" becomes "add stock for a product at a location" |
| `/dashboard/messaging` | 🔴 `messageBody` is dropped, stats are faked | campaigns | `POST /campaigns` (with body) |
| `/dashboard/analytics` | 🔴 fully hardcoded | timeline, top products, channels | `GET /analytics?range=` |
| `/dashboard/expenses` | 🟡 | expenses; top category and margin hardcoded | `POST /expenses`, `GET /expenses/summary` |
| `/dashboard/deliveries` | 🔴 ETA hardcoded, tracking code random | deliveries | `POST /deliveries`, `PATCH /deliveries/{id}/status` |
| `/dashboard/wallet` | 🔴 bank hardcoded, payout fake | balances, transactions | `GET /wallet`, `POST /wallet/payouts`, `PUT /wallet/bank-account` |
| `/dashboard/settings` | 🟡 + 🟢 (mode) | settings, team, mode | `PUT /settings`, `POST /team/invites` |
| `/dashboard/store` | static hub | — | — |
| `/dashboard/store/theme` | 🟢 | full theme | Save → `PUT /theme`; Reset → `POST /theme/reset` |
| `/dashboard/store/theme/templates` | 🟢 | templates | `POST /theme/apply-template` |
| `/dashboard/store/domain` | 🔴 "Verify" is a `setTimeout` | domain state | `GET /domain`, `PUT /domain`, `POST /domain/verify` |
| `/dashboard/portfolio` | 🟢 | items | `POST/PUT/DELETE /portfolio` |
| `/dashboard/bookings` | 🟡 | inquiries, packages | `POST /bookings`, `PATCH /bookings/{id}/status` |
| `/dashboard/packages` | 🟡 edit forces `isActive=true` (bug) | packages | `POST/PUT/DELETE /packages`, `PATCH` toggles |

### Public storefront (`/store/**`)

These pages currently read the **logged-in merchant's** browser data. The backend needs tenant resolution by host or slug.

| Route | State | Endpoint |
|:--|:--|:--|
| layout (header, footer, announcement) | 🟢 cart badge hardcoded "2" | `GET /storefront/store` (store + theme) |
| `/store` | 🟢; 🔴 newsletter form has no handler | `GET /storefront/products?featured=true`, `GET /storefront/portfolio?featured=true`, `POST /storefront/newsletter` |
| `/store/shop` | 🟢; 🔴 "Add to cart" is fake | `GET /storefront/products?category=&sort=newest` |
| `/store/products/[slug]` | 🟢; 🔴 no cart | `GET /storefront/products/{slug}`; cart → `/storefront/cart` (Week 9) |
| `/store/about` | 🟢 | from `/storefront/store` |
| `/store/contact` | 🔴 form only shows "sent" | `POST /storefront/contact` |
| `/store/gallery`, `/[slug]` | 🟢 | `GET /storefront/portfolio`, `/storefront/portfolio/{slug}` |
| `/store/packages` | 🟡 | `GET /storefront/packages` |
| `/store/book` | 🟡 | `POST /storefront/bookings` |
| `/store/pages/[slug]` | 🟢 | from `/storefront/store` (pages are part of the theme) |

## 3. Inconsistencies and how the backend resolves them

| # | Problem in `/web` | Backend decision |
|:--|:--|:--|
| 1 | **Two stock systems:** `Product.stock` (lowered by orders and POS) and `InventoryItem` (per location, not linked) | `inventory_levels(product, variant?, location)` is the only stock. `Product.stock` in responses is the sum across locations. The inventory page lists levels joined with products. |
| 2 | **Two POS models:** `RegisterSession` (live) and `POSShiftSummary` / `POSSaleRecord` (seed) | One `register_sessions` table (with `cashierId`). POS sales **are** orders with `channel=pos` and a `registerSessionId`. History and daily summary are queries. |
| 3 | Orders copy customer name, email and phone, with no `customerId` | `orders.customer_id` (nullable for walk-in) **plus** a snapshot of name, email, phone and address at order time |
| 4 | Customer totals are typed-in numbers | Computed from orders (a view or columns updated in the same transaction) |
| 5 | Store name lives in 3 places (`UserRecord`, `StoreSettings`, `theme.global`) | `tenants.business_name` is the truth. `PUT /settings` updates `theme.global.storeName` too. |
| 6 | VAT in 2 places (`StoreSettings.vatPercent`, `POSReceiptSettings.vatPercent`) | One `vatPercent` in settings. POS settings only hold `enableVat`. |
| 7 | Deliveries reference orders by `orderNumber` string; inquiries copy `packageName` | Real foreign keys. The API still returns `orderNumber` / `packageName` for display. |
| 8 | Section ids from an in-memory counter (`hero-3`) can collide after reload | Server keeps ids as given, but rejects duplicates within a page. The frontend should switch to `crypto.randomUUID()`. |
| 9 | Dates mix epoch ms (products, portfolio, register) with ISO and free text (`"Just now"`, `"Aug 05"`) | API always uses ISO-8601 UTC strings. The frontend formats them for display. |
| 10 | `hasOnboarded` is never enforced | `/me` returns it, and the frontend redirects `/dashboard` → `/onboarding` when it's false |
| 11 | `messageBody` dropped; campaign stats faked | Stored; stats come only from real sends |
| 12 | `setCustomDomain` activates instantly | `pending` until DNS verification passes |
| 13 | Packages edit forces `isActive=true` | `PUT /packages/{id}` respects the field; fix the frontend too |
| 14 | Password min 6 | Min 8, enforced on the server |
| 15 | Storefront has no tenant in the URL | Host-based (`acme.punta.shop`, dev `acme.localhost:3000`). See [06](./06-frontend-integration.md). |

## 4. Exact frontend types the API must match

The API's JSON field names are the frontend's TypeScript field names (camelCase). Source files:

| Type | File |
|:--|:--|
| `Product`, `ProductVariant`, `Category` | `lib/products/types.ts` |
| `PortfolioItem` | `lib/portfolio/types.ts` |
| Theme (`GlobalThemeSettings`, `AnySection`, `CustomPage`, `NavLinkConfig`, page settings) | `lib/theme/schema.ts` |
| `ThemeTemplate`, `ThemeSnapshot` | `lib/theme/templates.ts` |
| `Order`, `OrderItem`, `Customer`, `InventoryItem`, `Delivery`, `Campaign`, `WalletTransaction`, `Expense`, `StoreSettings`, `TeamMember`, `DomainState`, `DnsRecord` | `lib/mock-dashboard-data.ts` |
| `POSSaleRecord`, `POSShiftSummary`, `POSDailySummary`, `POSReceiptSettings` | `lib/mock-pos-data.ts` |
| `RegisterSession`, `PaymentMethod` | `stores/register-store.ts` |
| `BookingInquiry`, `ServicePackage` | `lib/mock-portfolio-data.ts` |

When building a module, open its type file next to the DTO you're writing.
