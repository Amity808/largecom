# Week 8 — Public Storefront API & Domains

**Goal:** Any visitor can open `acme.localhost:3000` (dev) or `acme.punta.shop` (prod) and see Acme's live store without logging in. Contact and newsletter forms work. Custom domains are verified for real.

**Depends on:** Weeks 3, 4 and 7.

## Day 1 — Storefront tenant resolution
- [ ] `StorefrontTenantFilter` on `/api/v1/storefront/**`:
  1. `X-Store-Slug` header → `tenants.slug`.
  2. Else `Host`:
     - `{slug}.{STOREFRONT_ROOT_DOMAIN}` → slug
     - otherwise look the host up in `custom_domains` where `status='verified'`
  3. Not found or inactive → `404 {code: "NOT_FOUND", message: "Store not found"}`.
  4. Put the tenant in `TenantContext`.
- [ ] Cache slug and host → tenantId lookups (Caffeine, 5 min TTL; evict on slug or domain change).
- [ ] **Preview mode:** a logged-in merchant on `localhost:3000/store` sends their own slug. No special backend code needed.

## Day 2 — Public read endpoints
- [ ] `GET /storefront/store`: store info + theme document. Exclude the theme's `version` and anything internal.
- [ ] `GET /storefront/categories`, `/products` (`category`, `sort=newest|featured`, `featured=true`, `limit`), `/products/{slug}`.
  - Public product DTO: **no** `costKobo`, `barcode` or inventory internals.
  - `stock` = available quantity.
  - Only active variants.
  - `featured=true` follows `pickFeaturedProducts`: active + featured, else any active.
- [ ] `GET /storefront/portfolio[?featured=true]`, `/portfolio/{slug}`, `/packages` (active, sorted).
- [ ] HTTP caching: `Cache-Control: public, max-age=60` on public GETs.

## Day 3 — Public writes
- [ ] `V13__storefront_contact_newsletter.sql`.
- [ ] `POST /storefront/contact`:
  - only if `theme.contact.showForm`
  - store the message, plus a hash of the IP (never the raw IP)
  - notify the merchant in Week 10
- [ ] `POST /storefront/newsletter`: upsert by email, return `202` either way (no enumeration).
- [ ] A shared `PublicWriteGuard`: honeypot + Bucket4j rate limit per IP and tenant + a max body size.
- [ ] (P1) Dashboard: `GET /inbox/contact-messages`, `GET /newsletter/subscribers` (+ CSV).

## Day 4 — Custom domains
- [ ] `V14__custom_domains.sql`.
- [ ] `GET /domain`: `subdomain = slug + "." + root`, plus any custom domain and its records.
- [ ] `PUT /domain {customDomain}`:
  - normalise the hostname (lowercase, no scheme or path) and reject `*.punta.shop`
  - unique across tenants → `409`
  - generate a verification token
  - records:
    - `CNAME {host} → cname.punta.shop`
    - `TXT _punta-verify.{domain} → {token}`
- [ ] `POST /domain/verify`: real DNS lookups (dnsjava, or JNDI `InitialDirContext` with `dns:`). Set each record to `configured` or `pending`. All configured → `verified`, and `isCustomDomainActive=true`.
- [ ] TLS for custom domains is an infrastructure task (Cloudflare for SaaS / Caddy on-demand TLS), covered in Week 12.

## Day 5 — Frontend switch
- [ ] `proxy.ts`: on a host matching `*.{root}` (or any host that isn't the app host), rewrite `/`, `/shop`, `/products/*`, … → `/store/...`. Read the Next 16 proxy docs first.
- [ ] API client sends `X-Store-Slug` ([06 §2](../06-frontend-integration.md#2-a-tiny-api-client-week-2)).
- [ ] `/store/**` pages stop reading the dashboard stores and use `/storefront/*`. Prefer server components + `fetch`.
- [ ] Newsletter section: add a submit handler. Contact page → API.
- [ ] Remove the "Previewing… Edit theme" bar for non-owners (show it only when `/me` returns the same tenant).
- [ ] Domain page → API; "Verify DNS Records" performs a real check.

## Done when
- [ ] Logged out, in incognito, `acme.localhost:3000` shows Acme's theme and products, and `bob.localhost:3000` shows Bob's.
- [ ] An inactive product's slug URL → 404.
- [ ] The public JSON never contains `costKobo` (add a test that greps the response).
