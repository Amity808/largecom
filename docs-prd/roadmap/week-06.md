# Week 6 — Orders & POS

**Goal:** Manual orders and POS sales are real, atomic and idempotent. POS history shows real shifts and sales. **After this week a merchant can run a shop on Punta.**

**Depends on:** Weeks 4–5.

## Day 1 — Order numbers + order schema
- [ ] `V10__orders.sql`. Entities `Order` and `OrderItem` (snapshots of name, SKU and price).
- [ ] `OrderNumberService.next(tenantId)`: `UPDATE tenant_counters … RETURNING value` → `ORD-1001`. Test it with 50 parallel calls: no duplicates, no gaps that matter.
- [ ] `IdempotencyService` (table from V4):
  - `execute(key, requestHash, Supplier<Response>)`
  - stores the status and body
  - replays them for the same key; returns `409` for the same key with a different body

## Day 2 — Manual orders
- [ ] `OrderService.createManual(req)` in **one transaction**:
  1. Load the products and variants (tenant-scoped). Inactive or missing → 400.
  2. Price = variant price ?? product price. **Never the client price.**
  3. `InventoryService.decrement(items, defaultLocation, 'sale', orderId)` (`409 INSUFFICIENT_STOCK` on failure).
  4. Customer: `customerId`, or `findOrCreateForOrder`, or walk-in (null). Snapshot name, email and phone on the order.
  5. Totals:
     - `subtotal = Σ line`
     - `tax` = 0 for manual orders in v1
     - `total = subtotal - discount + shipping + tax`
  6. `CustomerService.recordOrder` if there's a customer.
- [ ] `GET /orders` (filters, search), `GET /orders/{id}`, `GET /orders/summary`.
- [ ] `PATCH /orders/{id}/status`. Allowed transitions:
  - `pending → processing | cancelled`
  - `processing → shipped | cancelled`
  - `shipped → delivered`
  - On `cancelled`: restock the items + `CustomerService.reverseOrder`.

## Day 3 — POS registers
- [ ] `V11__pos.sql`. `RegisterSession`, `PosSettings` (row created lazily with defaults).
- [ ] `GET /pos/registers/current`, `POST /pos/registers` (unique open per cashier → `409`), `POST /pos/registers/{id}/close`:
  - `expected = float + cashSales`
  - `overShort = actual − expected`
- [ ] `GET/PUT /pos/settings`.

## Day 4 — POS checkout
- [ ] `POST /pos/checkout` with a required `Idempotency-Key`, in **one transaction**:
  1. The session must be open and belong to the current user.
  2. Build the order like a manual one, but with:
     - `channel=pos`, `paymentStatus=paid`, `fulfillmentStatus=delivered`
     - `paymentMethod`, `registerSessionId`, `cashierId`
  3. If `pos_settings.enable_vat`: `tax = round(subtotal × vatPercent / 100)`. Use the half-up rule and `long` maths.
  4. Increment the session's `{cash|card|transfer}_sales_kobo` and `transaction_count` with an `UPDATE … SET x = x + :amt`, not read-modify-write.
  5. Return the order, the session and the receipt data.
- [ ] Tests:
  - double submit with the same key → one order
  - out of stock → 409 and **nothing** changed (session totals, customer, stock)
  - closed register → 409

## Day 5 — POS history + frontend switch
- [ ] `GET /pos/shifts?range=`, `GET /pos/sales?range=&search=` (orders where `channel='pos'` + the cashier's name), `GET /pos/summary?range=` (daily aggregation via `date_trunc('day', created_at AT TIME ZONE 'Africa/Lagos')`).
- [ ] `/web`:
  - `orders` page, `orders/new` (submit `productId` + quantity, not prices)
  - `pos` page:
    - open the register via the API
    - Charge → `/pos/checkout` with `crypto.randomUUID()` as the key, created **once per ticket**
    - close the register via the API
    - remove the client-side stock decrement
  - `pos/history` (range buttons now work), `pos/settings`
  - Delete `register-store.ts` persistence and `mock-pos-data.ts`.

## Done when
- [ ] **End-to-end:** open the register with ₦10,000 → 3 sales (cash, card, transfer) → close it with the counted cash. Then check:
  - stock went down
  - 3 orders with `channel=pos`
  - the shift row shows the correct totals and over/short
  - the customers' totals were updated
- [ ] Double-clicking "Charge" never creates two orders.
