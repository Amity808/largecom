# 05 — API Contract

Base: `/api/v1`. Conventions are in [03 §6](./03-backend-architecture.md#6-api-conventions):
- camelCase field names
- money as `…Kobo` integers
- ISO dates
- responses wrapped in `{ "data": … }`

**Auth column:**
- 🔓 public
- 👤 any member
- 🛠 OWNER or MANAGER
- 👑 OWNER only

---

## Auth — Week 2

| Method | Path | Auth | Body → Response |
|:--|:--|:--|:--|
| POST | `/auth/register` | 🔓 | `{storeName, email, password}` → `201 {user, tenant}` + sets cookies |
| POST | `/auth/login` | 🔓 | `{email, password}` → `{user, tenant}` + sets cookies. Bad credentials → `401 {code: "UNAUTHORIZED", message: "Invalid email or password"}` |
| POST | `/auth/refresh` | 🔓 (refresh cookie) | — → `204` + new cookies |
| POST | `/auth/logout` | 🔓 | — → `204`, revokes refresh token, clears cookies |
| GET | `/me` | 👤 | → `MeResponse` |

```jsonc
// MeResponse
{
  "user":   { "id": "…", "email": "ada@shop.ng", "fullName": null, "createdAt": "2026-10-16T09:00:00Z" },
  "tenant": { "id": "…", "slug": "ada-styles", "storeName": "Ada Styles", "businessMode": "ecommerce",
              "hasOnboarded": false, "storefrontUrl": "http://ada-styles.localhost:3000" },
  "role": "OWNER"
}
```
The frontend's `PublicUser` (`id, email, storeName, hasOnboarded, createdAt`) can be built from this.

## Templates & onboarding — Week 3

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/templates` | 🔓 | `[{id, name, industry, description, businessType, previewTheme}]` |
| POST | `/onboarding` | 👑 | `{templateId}` → `{theme, productsCreated, portfolioItemsCreated}`. Returns `409` if already onboarded. |

## Settings — Week 3

| Method | Path | Auth | Body |
|:--|:--|:--|:--|
| GET | `/settings` | 👤 | → `StoreSettings` |
| PUT | `/settings` | 🛠 | `StoreSettings` (all fields) |
| PUT | `/settings/business-mode` | 🛠 | `{businessMode: "ecommerce" \| "pos" \| "portfolio"}` |

```jsonc
// StoreSettings (= web lib/mock-dashboard-data.ts StoreSettings)
{ "storeName": "Ada Styles", "tagline": "…", "supportEmail": "…", "supportPhone": "…",
  "currency": "NGN", "vatPercent": 7.5, "autoFulfillDigital": false, "notifyOnNewOrder": true }
```

## Theme — Week 3

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/theme` | 👤 | → `{version, templateId, global, header, footer, about, contact, shop, sections, pages, navLinks, updatedAt}` |
| PUT | `/theme` | 🛠 | Same shape + `version`. If `version` is stale → `409 VERSION_CONFLICT`. Returns the new `version` and `updatedAt` (the frontend's `lastSavedAt`). |
| POST | `/theme/apply-template` | 🛠 | `{templateId}` → the theme. Keeps `pages`, resets about/contact/shop, and rebuilds the nav (same rules as `applyTemplate` in `theme-store.ts`). |
| POST | `/theme/reset` | 🛠 | Back to the defaults |

Validation:
- `sections[].type` must be one of the 9 `SectionType` values.
- Section ids must be unique within a page.
- `pages[].slug` must be unique.
- The document must be ≤ 512 KB.

## Uploads — Week 4

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| POST | `/uploads` | 👤 | `multipart/form-data` field `file` (jpeg/png/webp, ≤ 5 MB) → `{url, id}`. Dev: local disk served at `/files/**`. Prod: R2. |
| POST | `/uploads/presign` | 👤 | P1 (prod): `{filename, contentType}` → `{uploadUrl, publicUrl, id}` |

## Catalog — Week 4

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/categories` | 👤 | `[Category]` |
| POST | `/categories` | 🛠 | `{name}`. **Idempotent by slug**: returns the existing category with `200`, or creates one with `201`. |
| PATCH | `/categories/{id}` | 🛠 | `{name}` |
| DELETE | `/categories/{id}` | 🛠 | Products in it get `categoryId=null` |
| GET | `/products?search=&status=all\|active\|draft&categoryId=&page=&size=` | 👤 | `[Product]` |
| GET | `/products/{id}` | 👤 | `Product` |
| POST | `/products` | 🛠 | `ProductInput` → `201 Product` |
| PUT | `/products/{id}` | 🛠 | `ProductInput` |
| PATCH | `/products/{id}` | 🛠 | Partial, e.g. `{isActive}` / `{isFeatured}` (toggles) |
| DELETE | `/products/{id}` | 🛠 | Soft delete |
| POST | `/products/{id}/variants` | 🛠 | `{name, sku, priceKobo\|null, attributes, isActive}` |
| PATCH | `/products/{id}/variants/{vid}` | 🛠 | Partial |
| DELETE | `/products/{id}/variants/{vid}` | 🛠 | |

```jsonc
// Product (= web lib/products/types.ts)
{ "id": "…", "categoryId": "…", "name": "Classic Tee", "slug": "classic-tee", "description": "",
  "priceKobo": 1500000, "compareAtKobo": null, "costKobo": 800000, "sku": "TEE-01", "barcode": "",
  "isActive": true, "isFeatured": false, "tags": ["cotton"], "emoji": "👕", "swatch": "#111827",
  "imageUrl": "https://…", "stock": 24,
  "variants": [{ "id": "…", "name": "L / Red", "sku": "TEE-01-L-R", "priceKobo": null,
                 "attributes": {"size":"L","color":"Red"}, "isActive": true }],
  "createdAt": "…", "updatedAt": "…" }
// ProductInput = Product minus id, slug, variants, createdAt, updatedAt.
// stock on create = initial on_hand at the default location; on update it is IGNORED (use /inventory/adjust).
```

## Inventory — Week 5

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/locations` | 👤 | |
| POST | `/locations` | 🛠 | P1 (multi-location) |
| GET | `/inventory?status=low_stock&locationId=&search=` | 👤 | `[InventoryItem]` |
| POST | `/inventory/adjust` | 👤 | `{productId, variantId?, locationId?, delta, reason: "restock"\|"adjustment", reorderPoint?, unitCostKobo?}` → `InventoryItem` |
| GET | `/inventory/summary` | 👤 | `{itemCount, locationCount, lowStock, outOfStock, valuationKobo}` |
| GET | `/inventory/{productId}/movements` | 🛠 | Audit trail |

```jsonc
// InventoryItem (= web InventoryItem, with real links)
{ "id": "<inventory_level id>", "productId": "…", "variantId": null, "name": "Classic Tee", "sku": "TEE-01",
  "location": "Main Store", "locationId": "…", "stock": 24, "reserved": 2, "reorderPoint": 5,
  "unitCostKobo": 800000, "status": "in_stock" }
```

## Customers — Week 5

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/customers?search=&group=` | 👤 | `[Customer]` |
| GET | `/customers/summary` | 👤 | `{total, vipCount, totalLtvKobo, avgSpendKobo}` |
| GET | `/customers/{id}` | 👤 | Customer + recent orders |
| POST | `/customers` | 👤 | `{name, email, phone, group}`. Duplicate email or phone → `409` |
| PATCH | `/customers/{id}` | 👤 | |
| DELETE | `/customers/{id}` | 🛠 | Soft delete |

`Customer` = `{id, name, email, phone, group, totalOrders, totalSpentKobo, lastOrderDate, status}`.

## Orders — Week 6

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/orders?search=&status=&channel=&from=&to=` | 👤 | `[Order]` |
| GET | `/orders/summary` | 👤 | `{paidRevenueKobo, openCount, shippedCount, deliveredCount}` |
| GET | `/orders/{id}` | 👤 | |
| POST | `/orders` | 👤 | Manual order (below) |
| PATCH | `/orders/{id}/status` | 👤 | `{fulfillmentStatus}`. Moving to `cancelled` restocks the items. |
| PATCH | `/orders/{id}/payment-status` | 🛠 | `{paymentStatus}` (P1) |

```jsonc
// POST /orders
{ "customerId": null,                     // or an existing id
  "customer": { "name": "Tobi", "email": "", "phone": "0803…" },   // used if customerId is null; auto-creates a customer if phone/email given
  "shippingAddress": "12 Allen Ave, Ikeja",
  "items": [{ "productId": "…", "variantId": null, "quantity": 2 }],   // server looks up the price
  "paymentStatus": "unpaid", "paymentMethod": null }
// → 201 Order
{ "id": "…", "orderNumber": "ORD-1001", "customerId": "…", "customerName": "Tobi", "customerEmail": "",
  "customerPhone": "0803…", "items": [{ "id": "…", "productId": "…", "name": "Classic Tee", "quantity": 2, "priceKobo": 1500000 }],
  "totalKobo": 3000000, "paymentStatus": "unpaid", "fulfillmentStatus": "pending",
  "createdAt": "…", "shippingAddress": "…", "channel": "manual", "paymentMethod": null }
```

## POS — Week 6

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/pos/registers/current` | 👤 | → the open `RegisterSession` for the current user, or `204` |
| POST | `/pos/registers` | 👤 | `{openingFloatKobo}` → `201`. Returns `409` if one is already open. |
| POST | `/pos/registers/{id}/close` | 👤 | `{actualCashKobo}` → closed session with `expectedCashKobo` and `overShortKobo` |
| POST | `/pos/checkout` | 👤 | **Header `Idempotency-Key`**. Body below. |
| GET | `/pos/shifts?range=today\|7d\|30d` | 👤 | `[POSShiftSummary]` |
| GET | `/pos/sales?range=&search=` | 👤 | `[POSSaleRecord]` (from orders where channel=pos) |
| GET | `/pos/sales.csv?range=` | 🛠 | P1 |
| GET | `/pos/summary?range=` | 👤 | `{todaySalesKobo, todayTransactions, revenueKobo, transactions, avgTicketKobo, openRegisters, daily: [POSDailySummary]}` |
| GET/PUT | `/pos/settings` | 👤 / 🛠 | `POSReceiptSettings` (`vatPercent` is read from store settings) |

```jsonc
// POST /pos/checkout
{ "registerSessionId": "…", "customerId": null,
  "items": [{ "productId": "…", "variantId": null, "quantity": 1 }],
  "paymentMethod": "cash" }
// → 201 { "order": Order, "session": RegisterSession, "receipt": { "storeName": "…", "storePhone": "…", "footerMessage": "…",
//          "subtotalKobo": …, "taxKobo": …, "totalKobo": … } }
// Errors: 409 INSUFFICIENT_STOCK {details:[{field:"items[0]", message:"Only 1 left"}]}, 409 register closed
```

## Portfolio mode — Week 7

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET / POST | `/portfolio` | 👤 / 🛠 | `PortfolioItem` (`{title, category, description, imageUrl, isFeatured}`) |
| PUT / PATCH / DELETE | `/portfolio/{id}` | 🛠 | |
| GET / POST | `/packages` | 👤 / 🛠 | `ServicePackage` |
| PUT / PATCH / DELETE | `/packages/{id}` | 🛠 | `PATCH` handles the `isActive` / `isPopular` toggles |
| PUT | `/packages/order` | 🛠 | `{ids: [...]}` → sets `sortOrder` (P1) |
| GET | `/bookings?status=&search=` | 👤 | `[BookingInquiry]` (with a `packageName` field for display) |
| GET | `/bookings/summary` | 👤 | `{pending, confirmed, pipelineKobo, responseRatePercent}` |
| POST | `/bookings` | 👤 | Dashboard-created inquiry |
| PATCH | `/bookings/{id}/status` | 👤 | `{status}` |

## Public storefront — Week 8

The tenant is resolved from `X-Store-Slug` or `Host`. Unknown or inactive store → `404`. All public writes are rate-limited (e.g. 5 per minute per IP) and accept a `website` honeypot field; if the honeypot is filled, the server returns 200 and ignores the request.

| Method | Path | Notes |
|:--|:--|:--|
| GET | `/storefront/store` | `{store: {name, slug, tagline, supportEmail, supportPhone, currency, businessMode}, theme: {global, header, footer, about, contact, shop, sections, pages, navLinks}}` |
| GET | `/storefront/categories` | Only categories that have active products |
| GET | `/storefront/products?category=&sort=newest\|featured&featured=true&limit=` | Public `Product` **without** `costKobo` and `barcode`. `stock` is the available quantity. Only active variants. |
| GET | `/storefront/products/{slug}` | |
| GET | `/storefront/portfolio?featured=true&category=` | |
| GET | `/storefront/portfolio/{slug}` | |
| GET | `/storefront/packages` | Active, sorted by `sortOrder` |
| POST | `/storefront/bookings` | `{clientName, email, phone, eventType, preferredDate, packageId, message}` → `201 {id}` |
| POST | `/storefront/contact` | `{name, email, message}` → `202`. Returns `404` if `contact.showForm` is off. |
| POST | `/storefront/newsletter` | `{email}` → `202` (idempotent) |
| POST | `/storefront/events` | `{type, path}` → `204` (Week 11) |

## Domains — Week 8

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/domain` | 👤 | `DomainState`: `{subdomain: "acme.punta.shop", customDomain, isCustomDomainActive, dnsRecords: [{type, host, value, status}]}` |
| PUT | `/domain` | 👑 | `{customDomain}` → status `pending`, plus the records to add, including a TXT `_punta-verify` record |
| POST | `/domain/verify` | 👑 | Real DNS lookup → updates each record's `status`. Activates the domain only if every record matches. |
| DELETE | `/domain` | 👑 | Disconnect the custom domain |

## Cart, checkout, payments — Week 9

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/storefront/cart` | 🔓 cart cookie | `{items:[{productId, variantId, name, imageUrl, unitPriceKobo, quantity, lineTotalKobo}], subtotalKobo, itemCount}` |
| POST | `/storefront/cart/items` | 🔓 | `{productId, variantId, quantity}`. Creates the `punta_cart` cookie if missing. |
| PATCH / DELETE | `/storefront/cart/items/{itemId}` | 🔓 | |
| POST | `/storefront/checkout` | 🔓 + `Idempotency-Key` | `{customer:{name,email,phone}, shippingAddress}` → `{orderId, orderNumber, totalKobo, paymentUrl}` |
| GET | `/storefront/orders/{orderNumber}/status?ref=` | 🔓 | Shown after the Paystack redirect |
| POST | `/webhooks/paystack` | 🔓 + HMAC | Verify `x-paystack-signature` = HMAC-SHA512(rawBody, secret). Dedupe by event id. |

## Wallet — Week 9 (ledger) / Week 10 (payouts)

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/wallet` | 🛠 | `{availableBalanceKobo, pendingBalanceKobo, lifetimePayoutsKobo, bankAccount}` |
| GET | `/wallet/transactions?type=&page=` | 🛠 | `[WalletTransaction]`: `{id, reference, type, description, amountKobo, date, status}` |
| GET | `/wallet/banks` | 🛠 | Nigerian bank list (Paystack `/bank`, cached) |
| PUT | `/wallet/bank-account` | 👑 | `{bankCode, accountNumber}` → resolves the account name through Paystack and saves it, encrypted |
| POST | `/wallet/payouts` | 👑 + `Idempotency-Key` | `{amountKobo}`. Must be ≤ available. Creates a `pending` ledger debit and a payout request. |

## Expenses — Week 10

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/expenses?search=&category=&from=&to=` | 🛠 | `[Expense]`: `{id, date, description, category, vendor, amountKobo}` |
| GET | `/expenses/summary?range=30d` | 🛠 | `{totalKobo, topCategory, topCategoryPercent, revenueKobo, netMarginPercent}` |
| POST / PUT / DELETE | `/expenses[/{id}]` | 🛠 | |

## Deliveries — Week 10

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/deliveries?status=&courier=&search=` | 👤 | `[Delivery]` (with `orderNumber` and `customerName` from the order) |
| GET | `/deliveries/summary` | 👤 | `{total, inTransit, delivered, actionRequired}` |
| POST | `/deliveries` | 👤 | `{orderId, courier, address?, trackingCode?, estimatedArrival?}`. The address defaults to the order's shipping address. Also moves the order to `shipped`. |
| PATCH | `/deliveries/{id}/status` | 👤 | `{status}`. When it becomes `delivered`, the order becomes `delivered`. |

## Messaging — Week 10

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/campaigns?channel=&search=` | 🛠 | `[Campaign]` (stats come from recipients) |
| GET | `/campaigns/audiences` | 🛠 | `[{key: "all"\|"group:VIP"…, label: "VIP Customers", count: 58}]`, replacing the 3 hardcoded strings |
| POST | `/campaigns` | 🛠 | `{name, channel, audience, messageBody, sendNow \| scheduledAt}` |
| GET | `/campaigns/summary` | 🛠 | `{messagesSent, avgOpenRatePercent, byChannel:{…}}` |

## Team — Week 10

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/team` | 👤 | `[TeamMember]`: `{id, name, email, role: "Owner"\|"Manager"\|"Fulfillment Staff", status: "Active"\|"Pending"}` |
| POST | `/team/invites` | 👑 | `{name, email, role}` → emails an accept link |
| POST | `/auth/accept-invite` | 🔓 | `{token, password}` (creates the user if needed, activates the membership) |
| PATCH / DELETE | `/team/{membershipId}` | 👑 | Change role / remove member |

## Analytics & dashboard — Week 11

| Method | Path | Auth | Notes |
|:--|:--|:--|:--|
| GET | `/dashboard/summary` | 👤 | Cards for the tenant's mode (see FR-AN1), each `{value, changePercent}` |
| GET | `/analytics?range=7d\|30d\|90d\|1y` | 🛠 | Response below |

```jsonc
// GET /analytics response
{ "grossRevenueKobo": …, "revenueChangePercent": …, "aovKobo": …, "aovChangePercent": …,
  "conversionRatePercent": …, "visitors": …, "repeatCustomerRatePercent": …,
  "timeline": [{ "date": "2026-10-10", "salesKobo": …, "orders": … }],
  "topProducts": [{ "productId": "…", "name": "…", "salesCount": …, "revenueKobo": … }],
  "channels": [{ "channel": "web", "percentage": 58 }] }
```
