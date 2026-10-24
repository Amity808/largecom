# Week 11 — Analytics & Dashboards

**Goal:** Every number on `/dashboard`, `/dashboard/analytics` and the POS history KPIs comes from real data. No hardcoded figures remain in `/web`.

**Depends on:** Weeks 6, 9 and 10.

## Day 1 — Query layer
- [ ] `AnalyticsRepository` using `JdbcTemplate` / `NamedParameterJdbcTemplate` (**always** with `tenant_id = :tid`).
- [ ] Time zone: bucket by `Africa/Lagos` (`date_trunc('day', created_at AT TIME ZONE 'Africa/Lagos')`).
- [ ] Revenue = `SUM(total_kobo)` of orders with `payment_status='paid'` (minus refunded) in the range.
- [ ] Indexes: `orders(tenant_id, created_at)` already exists; add `orders(tenant_id, payment_status, created_at)` if `EXPLAIN` shows a sequential scan.

## Day 2 — Analytics endpoint
- [ ] `GET /analytics?range=7d|30d|90d|1y`:
  - `grossRevenueKobo` + % change vs the previous equal-length period
  - `aovKobo` (revenue ÷ paid orders) + % change
  - `repeatCustomerRatePercent` = customers with ≥ 2 paid orders ÷ customers with ≥ 1, in the range
  - `timeline[]` with zero-filled days (`generate_series`); weeks for 90d, months for 1y
  - `topProducts[]` (top 5 by revenue from `order_items`)
  - `channels[]` (% of revenue by `orders.channel`)

## Day 3 — Storefront events + conversion
- [ ] `V21__storefront_events.sql`. `POST /storefront/events {type, path}`:
  - `session_hash` = hash of the `punta_cart` token, or a `punta_vid` cookie
  - rate-limited
  - no PII
- [ ] `conversionRatePercent` = sessions with a paid order ÷ sessions with a `page_view`. `visitors` = distinct sessions.
- [ ] (P1) Rollup table `daily_sales_stats` + a nightly job if the queries get slow. Measure first.

## Day 4 — Dashboard summary per mode
- [ ] `GET /dashboard/summary` returns the cards for `tenant.business_mode`:
  - **ecommerce:** revenue 30d (+%), orders 30d, new customers 30d, live products
  - **pos:** today's sales, revenue 30d (+%), average ticket, open registers
  - **portfolio:** portfolio items, pending inquiries, confirmed bookings (+ this week), active packages
- [ ] Each card is `{key, label, value, displayValue?, changePercent?}`, so the frontend renders them generically.

## Day 5 — Frontend switch + cleanup
- [ ] `/dashboard`: cards from `/dashboard/summary`. Remove the hardcoded ₦184,500 / 312 / 58 / 46 and "+2 this week".
- [ ] `/dashboard/analytics`: range buttons drive `?range=`; remove `REVENUE_TIMELINE`, `TOP_PRODUCTS` and `CHANNELS`.
- [ ] POS history KPIs from `/pos/summary` (done in Week 6; double-check the 30d % change).
- [ ] The storefront layout sends `page_view` events (fire-and-forget).
- [ ] Search `/web` for leftover hardcoded money: `grep -rn "₦[0-9]" web/src/app/dashboard`.

## Done when
- [ ] Create 3 orders today → the dashboard revenue card and the analytics timeline both show them.
- [ ] Analytics for a brand-new tenant shows zeros and an empty-state chart. No errors, no NaN.
- [ ] p95 of `/analytics?range=1y` < 300 ms with 100k seeded orders (write a seeding script for a **separate** perf database).
