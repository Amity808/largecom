# Week 5 — Inventory & Customers

**Goal:** One stock system feeds the inventory page, product list, orders and POS. Customers are real records whose totals will come from orders (wired up in Week 6).

**Depends on:** Week 4.

## Day 1 — Inventory service
- [ ] `InventoryService`, the **only** class that changes stock. Every method writes an `inventory_movements` row in the same transaction.
  - `adjust(productId, variantId, locationId, delta, reason)`. Stock can never go below 0: the DB `CHECK` enforces it, and the service maps the violation to `409`.
  - `decrement(items, locationId, reason, referenceId)`: uses the conditional `UPDATE … WHERE on_hand - reserved >= :qty` from [03 §7](../03-backend-architecture.md#no-oversell-stock-decrement).
  - `reserve(...)` / `release(...)` / `commitReservation(...)`, used by checkout in Week 9.
  - `restock(items, reason='cancel', referenceId)`
- [ ] Every native query includes `tenant_id = :tid` (`@TenantId` doesn't cover native SQL).

## Day 2 — Inventory endpoints
- [ ] `GET /inventory`: one row per level, joined with the product (name, SKU), the variant and the location.
  - `status` is computed: `out_of_stock` when stock is 0, `low_stock` when stock ≤ `reorderPoint`, `in_stock` otherwise.
  - Filters: `status`, `locationId`, `search`.
- [ ] `POST /inventory/adjust` (the +1 / −1 / +10 buttons), which can also set `reorderPoint` and `unitCostKobo`.
- [ ] `GET /inventory/summary` (`valuationKobo = Σ on_hand × unit_cost`).
- [ ] `GET /locations`.
- [ ] **Concurrency test:** 20 parallel decrements of 1 against stock 10 → exactly 10 succeed, final stock is 0.

## Day 3 — Customers
- [ ] `V9__customers.sql`, `Customer` entity.
- [ ] `GET /customers?search=&group=`, `GET /customers/{id}`, `POST`, `PATCH`, `DELETE` (soft).
- [ ] Duplicate email or phone within a tenant → `409` with `details[].field`.
- [ ] `GET /customers/summary`.
- [ ] Normalise phone numbers (`0803…` and `+234803…` → one canonical `+234…` form) before saving and searching.

## Day 4 — Customer ↔ order hooks (prepare for Week 6)
- [ ] `CustomerService.findOrCreateForOrder(name, email, phone)`. Matches by phone, then by email; otherwise creates one with group `New`.
- [ ] `CustomerService.recordOrder(customerId, totalKobo, at)` and `reverseOrder(...)`, which update `total_orders`, `total_spent_kobo` and `last_order_at`.
- [ ] Optional rule: promote to `Returning` after 2 orders. Don't auto-assign `VIP`; the merchant sets it.

## Day 5 — Frontend switch
- [ ] Inventory page:
  - list from `/inventory`; quick-adjust buttons → `/inventory/adjust`
  - "Add Inventory Item" becomes **"Receive stock"**: pick a product (and variant), location and quantity. Creating products happens on the Products page.
  - Replace the hardcoded "Across 3 locations" with the summary's `locationCount`.
- [ ] Customers page → API; add Edit and Delete actions.
- [ ] Remove `inventory` and `customers` from `useDashboardStore`.

## Done when
- [ ] Adjusting stock on the inventory page changes the number on the products list, and vice versa.
- [ ] The concurrency test passes reliably (run it 10×).
- [ ] Every stock change has a matching movement row.
