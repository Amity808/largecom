# Punta Backend (`largecom`) — PRD & Build Plan

> Spring Boot backend for the Punta web app (`/web`, Next.js 16).
> This folder tells you **what to build, in what order, and how to know each step is done.**

## ⚠️ Rule zero: never lose the database

Before you write any code, read [07-database-safety.md](./07-database-safety.md). In short:

- The schema is changed **only** through Flyway migration files. Never through Hibernate.
- `spring.jpa.hibernate.ddl-auto=validate`, **never** `create`, `create-drop` or `update`.
- `spring.flyway.clean-disabled=true`, always.
- Tests run against a throwaway Testcontainers Postgres, **never** your real database.
- Take a `pg_dump` before running new migrations against a database you care about.

## How this relates to `docs/PUNTA/architecture` and `docs/PUNTA/roadmap`

The **Rust/Axum** plan in `docs/PUNTA/architecture` and `docs/PUNTA/roadmap` is still the long-term backend; it is **not** replaced. For now we are building in **Java/Spring Boot** (`largecom/`), and this folder (`largecom/docs-prd/`) is the plan for that Java build. It shares the decisions both backends have in common: PostgreSQL, money in kobo, UUID keys, `tenant_id` on every table, Paystack, and subdomain storefronts.

**Keep the Rust move possible.** The [API contract](./05-api-contract.md) and the [data model](./04-data-model.md) don't depend on the language. Plain SQL Flyway migrations, the JSON API shape and the cookie/JWT rules can all be reused by an Axum backend (SQLx can run the same SQL files). Avoid Java-only shortcuts that would leak into those contracts, such as Hibernate-generated schema or Java-specific serialization formats.

Where the Java plan differs from the Rust docs, follow this folder **while building `largecom`**. When the Rust backend starts, bring these changes back into `docs/PUNTA/architecture` (most of them came from what the frontend actually does, not from Java):

| Topic | Old docs | This PRD | Why |
|:--|:--|:--|:--|
| Language | Rust / Axum (long-term) | Java 25 / Spring Boot 4.1 (now) | Build in Java first; Rust plan stays
| POS | Out of scope | **In scope** | The web app already has a working POS screen |
| Portfolio mode | Not mentioned | **In scope** (portfolio, packages, bookings) | The web app has it |
| JSON casing | `snake_case` | **`camelCase`** | Matches the frontend's TypeScript types 1:1 (`priceKobo`) |
| Users | `users.tenant_id` | Global `users` + `memberships` | Login form has only email + password, no store field |
| Tenant isolation | RLS first | Hibernate `@TenantId` first, RLS added in Week 12 | Simpler to get right with JPA; RLS becomes the safety net |

## Documents

Read them in this order.

| # | Doc | What it answers |
|:--|:--|:--|
| 01 | [Product requirements](./01-product-requirements.md) | What the backend must do, module by module |
| 02 | [Frontend audit](./02-frontend-audit.md) | Every page in `/web`, what data it needs, what's real vs fake today |
| 03 | [Backend architecture](./03-backend-architecture.md) | Stack, package layout, tenancy, auth, conventions |
| 04 | [Data model](./04-data-model.md) | Tables and Flyway migration order |
| 05 | [API contract](./05-api-contract.md) | Every endpoint, with request/response shapes matching the frontend |
| 06 | [Frontend integration](./06-frontend-integration.md) | What changes in `/web` to switch from local stores to the API |
| 07 | [Database safety](./07-database-safety.md) | How to never drop or corrupt your data |

## 12-week plan

Each week has its own file in [roadmap/](./roadmap/) with day-by-day tasks and a "done when" checklist. Every week ends with **one frontend screen switched from mock data to the real API**, so progress is always visible.

| Week | Theme | Frontend screens that go live |
|:--|:--|:--|
| [01](./roadmap/week-01.md) | Foundation: pom fixes, Postgres, Flyway, errors, tests | — (health check) |
| [02](./roadmap/week-02.md) | Auth & multi-tenancy | `/register`, `/login`, logout, route guard |
| [03](./roadmap/week-03.md) | Store settings, theme, templates, onboarding | `/onboarding`, `/dashboard/settings`, theme editor |
| [04](./roadmap/week-04.md) | Catalog: categories, products, variants, images | `/dashboard/products/**` |
| [05](./roadmap/week-05.md) | Inventory & customers | `/dashboard/inventory`, `/dashboard/customers` |
| [06](./roadmap/week-06.md) | Orders & POS | `/dashboard/orders/**`, `/dashboard/pos/**` |
| [07](./roadmap/week-07.md) | Portfolio mode | `/dashboard/portfolio`, `/bookings`, `/packages` |
| [08](./roadmap/week-08.md) | Public storefront API & domains | `/store/**` (read-only), `/dashboard/store/domain` |
| [09](./roadmap/week-09.md) | Cart, checkout, Paystack, wallet ledger | Storefront cart/checkout, `/dashboard/wallet` |
| [10](./roadmap/week-10.md) | Expenses, deliveries, messaging, team | `/dashboard/expenses`, `/deliveries`, `/messaging`, team tab |
| [11](./roadmap/week-11.md) | Analytics & dashboards | `/dashboard`, `/dashboard/analytics`, POS history KPIs |
| [12](./roadmap/week-12.md) | Hardening & deploy | — (RLS, rate limits, Docker, CI, prod) |

```
W1 ─▶ W2 ─▶ W3 ─▶ W4 ─▶ W5 ─▶ W6 ─┬─▶ W8 ─▶ W9 ─▶ W10 ─▶ W11 ─▶ W12
                    │               │
                    └──▶ W7 ────────┘     (W7 can run in parallel with W5–W6)
```

Weeks 1–6 are the **critical path**. After Week 6, a merchant can sign up, set up a store, add products and sell in person through the POS, all on real data.

## Working rhythm (every task)

1. Write or extend the Flyway migration (new file, never edit an applied one).
2. Entity + repository.
3. Service with business rules, plus a unit test.
4. Controller + DTOs matching [05-api-contract.md](./05-api-contract.md).
5. Integration test (Testcontainers) including a **cross-tenant test**: tenant B must never see tenant A's rows.
6. Switch the matching frontend store to call the API ([06](./06-frontend-integration.md)).
7. Commit.
