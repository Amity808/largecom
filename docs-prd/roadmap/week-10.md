# Week 10 — Expenses, Deliveries, Messaging, Team, Payouts

**Goal:** Replace the remaining in-memory dashboard pages with real modules.

**Depends on:** Week 6 (orders), Week 9 (ledger).

## Day 1 — Expenses
- [ ] `V17__expenses.sql`. CRUD `/expenses`, filters (category, search, date range).
- [ ] `GET /expenses/summary?range=30d`:
  - `totalKobo`, `topCategory` and its %
  - `revenueKobo` (paid orders in the range)
  - `netMarginPercent = (revenue − expenses) / revenue × 100`, or `null` if there's no revenue
- [ ] Frontend: expenses page → API; replace the hardcoded "Top category 62%" and "Net margin +34.8%"; add the missing "Staff" filter chip.

## Day 2 — Deliveries
- [ ] `V18__deliveries.sql`. `POST /deliveries {orderId, courier, address?, trackingCode?, estimatedArrival?}`:
  - The order must be in the tenant and not cancelled.
  - The address defaults to the order's shipping address.
  - Auto tracking code `PNT-{orderNumber}-{4 random}` if none is given.
  - Moves the order to `shipped` (`OrderService.updateStatus`).
- [ ] `PATCH /deliveries/{id}/status`. `delivered` → order `delivered`.
- [ ] `GET /deliveries`, `/deliveries/summary`.
- [ ] Frontend: "Book Shipment" picks an **order** from a dropdown of paid, unshipped orders instead of a typed order number. ETA is a date picker.

## Day 3 — Email + messaging campaigns
- [ ] `EmailSender` interface: `ResendEmailSender` (prod) and `LoggingEmailSender` (dev/test).
- [ ] Wire up the earlier notifications: booking received, contact message, new order (if `notifyOnNewOrder`).
- [ ] `V19__campaigns.sql`. `GET /campaigns/audiences` from customer groups (+ "All customers", + marketing consent only).
- [ ] `POST /campaigns {name, channel, audience, messageBody, sendNow|scheduledAt}`:
  - snapshot the recipients
  - `@Scheduled` job sends in batches
  - template variables `{{name}}`, `{{discount}}`
  - email works for real; SMS and WhatsApp are logged as "provider not configured" and the recipient is marked `failed`
- [ ] Stats from `campaign_recipients` (`sent`, `opened` via a tracking pixel endpoint for email). P2: clicks.
- [ ] Frontend: messaging page → API; **send `messageBody`**; remove the hardcoded stats cards, or compute them.

## Day 4 — Team invites
- [ ] `V20__team_invites.sql`. `POST /team/invites` (OWNER): emails the link `/{APP_ORIGIN}/accept-invite?token=…`. Store only the token's hash; it expires after 7 days.
- [ ] `POST /auth/accept-invite {token, password, fullName?}`: creates the user if new, activates the membership, logs them in.
- [ ] `GET /team`, `PATCH /team/{id}` (role), `DELETE /team/{id}`. You can't remove the last OWNER.
- [ ] Apply the `@PreAuthorize` rules from [03 §5.3](../03-backend-architecture.md#53-authorization) across all controllers, plus a test matrix for STAFF (403 on settings, wallet, team).
- [ ] Frontend: Team tab → API. New `/accept-invite` page.

## Day 5 — Payouts (manual processing)
- [ ] `GET /wallet/banks` (Paystack `GET /bank?country=nigeria`, cached 24 h).
- [ ] `PUT /wallet/bank-account`:
  - resolve the account name with Paystack `GET /bank/resolve`
  - store the number **encrypted** (AES-GCM, key from env) + `last4`
- [ ] `POST /wallet/payouts` (OWNER, `Idempotency-Key`):
  - `amount ≤ available`; take a `SELECT … FOR UPDATE` on a per-tenant lock row so two payouts can't both pass the check
  - ledger `−amount`, `payout`, `pending` + a `payout_requests` row
- [ ] Ops: process payouts manually in v1 and mark them paid or failed through an internal endpoint or SQL script. A failed payout reverses the ledger entry with a new `+amount` entry; **never delete ledger rows**.
- [ ] Frontend: Wallet "Request Payout" → API; show the real saved bank account.

## Done when
- [ ] `useDashboardStore` in `/web` has no data left. Delete `mock-dashboard-data.ts`.
- [ ] A STAFF user can run the POS and update deliveries, but gets 403 on Wallet and Settings.
- [ ] Two simultaneous payout requests for the full balance → one succeeds, one gets 409.
