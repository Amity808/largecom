# Week 12 — Hardening & Deploy

**Goal:** Production-safe: database-enforced isolation, least-privilege DB roles, rate limits, observability, containers, CI and a real deployment with backups.

## Day 1 — Row-Level Security (defence in depth)
- [ ] `V22__rls_policies.sql`: for every tenant table, `ENABLE` + `FORCE ROW LEVEL SECURITY` + a policy on `current_setting('app.current_tenant', true)::uuid` (pattern in `docs/PUNTA/architecture/03-multi-tenancy.md`).
- [ ] Set the tenant at the start of every transaction by running `SELECT set_config('app.current_tenant', :tid, true)`. The `true` makes it transaction-local, so a pooled connection never leaks a tenant. Options:
  - **Simplest:** an aspect around `@Transactional` service methods that runs the statement right after the transaction begins.
  - **More robust:** a `DataSource` wrapper that sets it on every connection checkout, so `JdbcTemplate` code is covered too.
  - Storefront and webhook paths set it as well, once they know the tenant.
- [ ] Global tables (`tenants`, `users`, `memberships`, `refresh_tokens`, `webhook_events`, `custom_domains` lookups) stay **without** RLS, or get explicit policies for the lookup paths.
- [ ] Run the full test suite: everything must still pass. Add a test that runs native SQL **without** a tenant filter and still sees only one tenant's rows.

## Day 2 — Least-privilege roles
- [ ] `V23__app_role_grants.sql` (runs as the owner):
  ```sql
  -- role punta_app is created once by an admin, outside migrations (it needs a password)
  GRANT USAGE ON SCHEMA public TO punta_app;
  GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO punta_app;
  GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO punta_app;
  ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO punta_app;
  ```
- [ ] App connects as `punta_app`; Flyway connects as `punta_owner` (`spring.flyway.user/password`). Now the app **cannot** drop or truncate tables, and RLS applies.
- [ ] Optional: `REVOKE DELETE ON ledger_entries FROM punta_app`, so the ledger is append-only at the database level.

## Day 3 — Security pass
- [ ] Rate limits:
  - `/auth/login`: 5 per minute per IP+email
  - `/auth/register`: 3 per hour per IP
  - public writes (already in place)
  - Move to Redis-backed Bucket4j only if you run more than one instance.
- [ ] Security headers on the API (`X-Content-Type-Options`, `Referrer-Policy`); HSTS at the proxy.
- [ ] Log redaction: never log the `password`, cookie, `Authorization` or account number fields. Add a test that captures logs during login.
- [ ] Dependency check: `./mvnw org.owasp:dependency-check-maven:check`, or GitHub Dependabot.
- [ ] Go through `docs/PUNTA/architecture/09-security.md` and tick off what applies.

## Day 4 — Containers + CI
- [ ] `Dockerfile` (multi-stage, `eclipse-temurin:25-jre`, non-root user), or `./mvnw spring-boot:build-image`.
- [ ] GitHub Actions:
  - `./mvnw verify` (Testcontainers works on GitHub runners)
  - build the image
  - the web app's `npm run lint && npm run build`
- [ ] Structured JSON logging (`logging.structured.format.console=ecs`) with `requestId` and `tenantId` in MDC.
- [ ] Actuator: expose only `health` and `info` publicly; keep `metrics` / `prometheus` internal.

## Day 5 — Deploy
- [ ] Managed Postgres with automatic backups + PITR (Neon, Supabase or RDS). **Restore a backup into a new DB once, to prove it works.**
- [ ] Before the first prod migration, the deploy runs `pg_dump`, then Flyway, then starts the app. Migration and app start are separate steps.
- [ ] Host the API (Fly.io, Render, Railway or a VPS) and the web app (Vercel or the same host).
  - Same parent domain: `app.punta.shop` (web), with `/api` proxied to the API.
  - Storefronts on `*.punta.shop` via wildcard DNS + TLS (Cloudflare).
  - Custom domains via Cloudflare for SaaS or Caddy on-demand TLS.
- [ ] Switch `storage.provider=s3` (Cloudflare R2) and `PAYSTACK_SECRET_KEY` to live **only** after a test-mode end-to-end run in prod.
- [ ] Smoke test the 5 success criteria in [01 §7](../01-product-requirements.md#7-success-criteria-for-v1).

## Done when
- [ ] Production runs as `punta_app`; `DROP TABLE products;` as that role fails with "must be owner".
- [ ] Backup restore has been rehearsed and documented.
- [ ] CI blocks a merge when any test fails, including the cross-tenant tests.
