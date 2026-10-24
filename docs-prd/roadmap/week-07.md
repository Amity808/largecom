# Week 7 — Portfolio Mode (Portfolio, Packages, Bookings)

**Goal:** Everything a photographer or creative merchant uses is real: portfolio items, service packages and booking inquiries.

**Depends on:** Weeks 2–4 (tenancy, uploads). It can run **in parallel** with Weeks 5–6, since it doesn't touch orders or stock.

## Day 1 — Schema + portfolio items
- [ ] `V12__portfolio_packages_bookings.sql`.
- [ ] `PortfolioItem`:
  - `GET /portfolio`, `POST`, `PUT /{id}`, `PATCH /{id}` (`isFeatured`), `DELETE`
  - slug from the title, unique per tenant, regenerated when the title changes
  - `imageUrl` is required. The frontend currently falls back to picsum; the backend requires an uploaded or pasted URL.
- [ ] `PortfolioOnboardingSeeder` (plugs into Week 3's `OnboardingSeeder`) seeds the `photography-portfolio` template items.

## Day 2 — Service packages
- [ ] `GET /packages`, `POST`, `PUT /{id}`, `PATCH /{id}` (`isActive`, `isPopular`), `DELETE`.
  - `sortOrder` on create = max + 1.
  - `features` is a list of strings; trim it and drop empties.
- [ ] Deleting a package that has inquiries: `ON DELETE SET NULL` keeps the inquiry. The API shows `packageName: "Package removed"`.
- [ ] `PUT /packages/order {ids}` (P1).

## Day 3 — Booking inquiries
- [ ] `GET /bookings?status=&search=` returns `packageName` via a join (no copied column).
- [ ] `POST /bookings` (dashboard, `source='dashboard'`).
- [ ] `PATCH /bookings/{id}/status`. Any status → any status is fine in v1.
- [ ] `GET /bookings/summary`:
  - `pending` = count of `new`
  - `confirmed`
  - `pipelineKobo` = Σ package price for confirmed inquiries
  - `responseRatePercent` = non-new / total × 100, rounded to 1 decimal place

## Day 4 — Public booking endpoint (early)
- [ ] `POST /storefront/bookings`. Week 8 builds the full storefront tenant resolution. For now, accept `X-Store-Slug` only.
  - Validate `packageId` belongs to the tenant **and** is active.
  - `preferredDate` must be today or later.
  - Honeypot `website` field.
  - Rate limit: 5 per minute per IP. Bucket4j in memory is fine.
- [ ] (P1) `NotificationService.bookingReceived(...)`: an interface with a logging implementation now; it sends email in Week 10.

## Day 5 — Tests + frontend switch
- [ ] Tests:
  - cross-tenant access for all three entities
  - a public booking with another tenant's `packageId` → 400
  - an inactive package → 400
- [ ] `/web`:
  - `portfolio-store.ts` → API
  - `/dashboard/packages` → API. **Fix:** the edit form must send the real `isActive`, not force `true`.
  - `/dashboard/bookings` → API
  - `/store/book` → `POST /storefront/bookings`
  - Delete `mock-portfolio-data.ts`.

## Done when
- [ ] A visitor submits `/store/book` in an incognito window (with the slug header for now), and the inquiry appears in the merchant's dashboard on another device.
- [ ] The dashboard cards in portfolio mode read real counts (fully wired up in Week 11).
