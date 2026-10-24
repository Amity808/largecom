# Week 9 — Cart, Checkout, Paystack, Wallet Ledger

**Goal:** A shopper can add to cart, pay with Paystack (test mode), and the order shows up as paid with stock reserved then committed. Payment money lands in the merchant's wallet ledger.

**Depends on:** Weeks 6 and 8. **Needs:** a Paystack test account (`sk_test_…`).

## Day 1 — Cart
- [ ] `V15__carts_payments.sql`.
- [ ] Cart identified by the `punta_cart` cookie:
  - httpOnly, Lax, 30 days
  - the cookie holds a random token; the database stores only its hash
  - cart scoped to the tenant
- [ ] `GET /storefront/cart`, `POST /storefront/cart/items` (merge the same product and variant), `PATCH` / `DELETE /storefront/cart/items/{id}`.
- [ ] Prices are always computed live from the catalog. Cap quantity at available stock (return a warning, not an error).
- [ ] Nightly `@Scheduled` cleanup of carts older than 30 days. **Deletes cart rows only.**

## Day 2 — Checkout + reservation
- [ ] `POST /storefront/checkout` (`Idempotency-Key`), in one transaction:
  1. Validate the cart is non-empty and the items are active.
  2. `InventoryService.reserve(...)` (`reserved += qty`, still with the no-oversell condition).
  3. Create the order: `channel=web`, `paymentStatus=unpaid`, `pending`. Find or create the customer.
  4. Insert `payments(status=initialized, reference = "PNT-" + orderNumber + "-" + shortRandom)`.
- [ ] After commit, call Paystack `POST /transaction/initialize` with `{email, amount: totalKobo, reference, callback_url}`. Paystack amounts are already in kobo.
- [ ] Return `{orderId, orderNumber, totalKobo, paymentUrl}`.
- [ ] Scheduled job: unpaid orders older than 30 minutes → release the reservation, payment `abandoned`, order `cancelled`.

## Day 3 — Webhook
- [ ] `POST /webhooks/paystack`:
  - read the **raw body**
  - HMAC-SHA512 with the secret key; compare against `x-paystack-signature` using a constant-time comparison
  - mismatch → 401
- [ ] Dedupe: insert into `webhook_events(provider, event_id)`. On conflict, return `200` and do nothing.
- [ ] `charge.success`:
  1. Re-verify with `GET /transaction/verify/{reference}`. Never trust the webhook body alone.
  2. Check the amount equals the order total.
  3. In one transaction:
     - payment `success`
     - order `paid`
     - `InventoryService.commitReservation` (reserved → sold)
     - `CustomerService.recordOrder`
     - ledger entry
- [ ] Always respond `200` quickly. Do slow work after commit.
- [ ] Local testing: expose `localhost:8080` with a tunnel (cloudflared / ngrok) and set the webhook URL in the Paystack dashboard.

## Day 4 — Wallet ledger
- [ ] `V16__wallet_ledger.sql`.
- [ ] On a successful payment, write ledger entries:
  - `+total`: `order_settlement`, `completed`, `available_at = next business day 00:00 Africa/Lagos` (T+1)
  - `−fee` (if you take a platform fee): `fee`
- [ ] `GET /wallet` (available / pending / lifetimePayouts via SQL sums), `GET /wallet/transactions`.
- [ ] Tests: replaying the same webhook 3× → exactly one settlement entry; the sum of entries always equals what the UI shows.

## Day 5 — Frontend
- [ ] Storefront:
  - cart drawer/page
  - `ProductCard` and product page "Add to cart" → API
  - header cart badge = `itemCount` (replace the hardcoded "2")
  - checkout form → redirect to `paymentUrl`
  - `/store/order/[orderNumber]` status page (polls until paid)
- [ ] `/dashboard/wallet` balances and transactions → API. Keep the Payout button disabled until Week 10.
- [ ] `/dashboard/orders` now shows `channel=web` paid orders.

## Done when
- [ ] Paystack test card `4084 0840 8408 4081` → order paid, stock committed, wallet pending balance up by the total.
- [ ] Closing the Paystack tab without paying → after 30 minutes the reservation is released.
- [ ] A forged webhook (bad signature) is rejected and logged.
