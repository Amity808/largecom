# Week 1 — Foundation

**Goal:** `largecom` starts, connects to Postgres **safely**, runs Flyway, returns errors in the agreed format, and has a passing Testcontainers test. No business features yet.

**Read first:** [07-database-safety.md](../07-database-safety.md), [03 §1–2](../03-backend-architecture.md)

## Day 1 — Fix the project
- [ ] `pom.xml`:
  - remove `<version>4.1.0</version>` from the web starter, so the parent manages it
  - use `spring-boot-starter-webmvc` (Boot 4 name)
- [ ] Add the dependencies (generate the list at start.spring.io, see [03 §2](../03-backend-architecture.md#2-stack)):
  - data-jpa, postgresql, flyway (+ `flyway-database-postgresql`)
  - validation, actuator
  - security + oauth2-resource-server (configured in Week 2; for now permit all)
  - testcontainers, spring-boot-testcontainers
- [ ] Delete `HomeControler.java`.
- [ ] Create the package skeleton: `common/{api,error,jpa,config}`, `tenancy`, `auth`.
- [ ] `./mvnw -q compile` passes.

## Day 2 — Safe database connection
- [ ] **If you already have a database you care about:** `pg_dump` it now (see 07). Decide between *baseline* and *new database*.
- [ ] `compose.yaml` with an `external` volume, **or** point at your existing local Postgres.
- [ ] `application.properties`:
  - `ddl-auto=validate`
  - `clean-disabled=true`
  - `open-in-view=false`
  - datasource from env vars
  - `spring.threads.virtual.enabled=true`
- [ ] `application-dev.properties` (logging only). Create `.env` and add it to `.gitignore`.
- [ ] Add `SchemaSafetyGuard`. Test it: set `ddl-auto=create` and confirm the app **refuses to start**, then revert.

## Day 3 — Flyway + base entity
- [ ] `db/migration/V1__extensions.sql` (pgcrypto, pg_trgm, citext).
- [ ] `BaseEntity` (`@Id UUID`, `createdAt`, `updatedAt` via `@CreationTimestamp` / `@UpdateTimestamp`).
- [ ] App starts; `flyway_schema_history` shows V1.
- [ ] `Slugifier` with unit tests copied from the behaviour of `web/src/lib/slugify.ts` (`"Hello World!"` → `"hello-world"`).

## Day 4 — API envelope + errors
- [ ] `ApiResponse<T>(T data)`, `PageResponse<T>`, `ErrorResponse(error: {code, message, details[]})`.
- [ ] `ErrorCode` enum + `AppException(ErrorCode, message)`.
- [ ] `GlobalExceptionHandler` (`@RestControllerAdvice`), which handles:
  - `MethodArgumentNotValidException` → 400 with field details
  - `AppException` → its status
  - `OptimisticLockException` → 409 `VERSION_CONFLICT`
  - `DataIntegrityViolationException` → 409 `CONFLICT`
  - anything else → 500 with no stack trace in the body
- [ ] Jackson: ISO dates (no timestamps), `NON_NULL` off (the frontend expects explicit `null`s).
- [ ] CORS for `APP_ORIGIN` (only needed if you skip the Next rewrite).

## Day 5 — Tests + tooling
- [ ] `TestcontainersConfiguration` with `@ServiceConnection PostgreSQLContainer("postgres:16")`.
- [ ] `LargecomApplicationTests.contextLoads()` runs against the container. Confirm `application-test.properties` has **no** DB URL.
- [ ] springdoc → `/swagger-ui.html`.
- [ ] `/actuator/health` shows `UP` with the db component.
- [ ] README in `largecom/` covering how to run (env vars, `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`).
- [ ] Commit.

## Done when
- [ ] `./mvnw verify` is green, and tests never touch the dev DB.
- [ ] Restarting the app 3 times leaves your data and schema unchanged.
- [ ] Setting `ddl-auto=create` makes startup fail with a clear message.
- [ ] `curl localhost:8080/actuator/health` → `{"status":"UP"}`.

## Pitfalls
- Mixing Boot 3 and Boot 4 artifacts. Let the parent POM manage **every** Spring version.
- Hibernate `validate` fails if an entity field has no column. That's the point: add a migration, never switch to `update`.
