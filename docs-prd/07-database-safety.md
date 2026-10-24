# 07 — Database Safety

> Goal: **no command, test, config change or deploy can ever drop or wipe your database.**
> Do this setup in Week 1, before the first entity exists.

## The five ways people lose a Spring Boot database

| # | How it happens | Prevention |
|:--|:--|:--|
| 1 | `spring.jpa.hibernate.ddl-auto=create` or `create-drop` drops all tables on startup or shutdown | Set `ddl-auto=validate` in **every** profile |
| 2 | `ddl-auto=update` silently alters columns and never removes old ones, so the schema drifts | Use Flyway for all schema changes |
| 3 | `flyway clean` (CLI, Maven plugin, or `cleanOnValidationError` in older Flyway versions) drops every object in the schema | `spring.flyway.clean-disabled=true`; never add the Flyway Maven plugin's `clean` goal to a build |
| 4 | Integration tests point at the dev database and `@Sql` scripts or `deleteAll()` wipe it | Tests use **Testcontainers** only; the test profile has no real DB URL |
| 5 | `docker compose down -v` deletes the named volume that holds Postgres data | Never use `-v`; mark the volume `external` (see below) |

## Required configuration

`src/main/resources/application.properties` (shared defaults):

```properties
spring.application.name=largecom
server.port=8080

# --- Schema is owned by Flyway, never Hibernate ---
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false

spring.flyway.enabled=true
spring.flyway.clean-disabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.validate-on-migrate=true

# --- Connection comes from env vars, never committed ---
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
```

`application-dev.properties` holds **only** local overrides such as logging and CORS. It does not change `ddl-auto` or the Flyway settings.

Add a startup guard so a bad override can never slip in:

```java
// common/config/SchemaSafetyGuard.java
// A BeanFactoryPostProcessor runs BEFORE any bean (DataSource, Flyway, Hibernate) is created,
// so a dangerous setting stops the app before it can touch the database.
@Component
public class SchemaSafetyGuard implements BeanFactoryPostProcessor, EnvironmentAware {
    private Environment env;

    @Override public void setEnvironment(Environment env) { this.env = env; }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory bf) {
        var ddl = env.getProperty("spring.jpa.hibernate.ddl-auto", "validate");
        if (!ddl.equals("validate") && !ddl.equals("none")) {
            throw new IllegalStateException("Refusing to start: ddl-auto=" + ddl + " can destroy data");
        }
        if (!env.getProperty("spring.flyway.clean-disabled", Boolean.class, true)) {
            throw new IllegalStateException("Refusing to start: flyway clean is enabled");
        }
    }
}
```

## If your database already has tables

If the database you're pointing at already holds data from an earlier experiment, **do not** let Flyway start from V1. Baseline it instead:

1. Back it up first: `pg_dump -Fc -d "$DB_URL" -f backup-$(date +%F).dump`
2. Compare what's there with [04-data-model.md](./04-data-model.md).
3. Choose one option:
   - **Keep it:** set `spring.flyway.baseline-on-migrate=true` and `spring.flyway.baseline-version=0`, and write V1+ migrations that use `CREATE TABLE IF NOT EXISTS` / `ALTER TABLE … ADD COLUMN IF NOT EXISTS` for the overlapping tables.
   - **Start clean:** create a **new** database (`createdb punta_dev`) and point `DB_URL` at it. The old one stays untouched.

Neither option drops anything.

## Migration rules

- File naming: `V{n}__{description}.sql`, e.g. `V7__create_products.sql`. Numbers only go up.
- **Never edit a migration that has already run** anywhere. Flyway's checksum will fail, and the only "fix" people reach for is `clean`. Write a new migration instead.
- Destructive changes (dropping or renaming a column) are done in **two releases**:
  1. Add the new column, write to both, backfill.
  2. In a later migration, drop the old column, after a backup.
- Every migration that deletes data must have a comment `-- DESTRUCTIVE: reviewed <date>` and is reviewed before merge.

## Database roles (Week 12, recommended from day one)

| Role | Used by | Privileges |
|:--|:--|:--|
| `punta_owner` | Flyway only (migrations) | Owns the schema; can CREATE/ALTER |
| `punta_app` | The running app | `SELECT, INSERT, UPDATE, DELETE` on tables; **no** `DROP`, `TRUNCATE`, or `ALTER` |

With `spring.flyway.user` / `spring.flyway.password` set to the owner, and `spring.datasource.*` set to `punta_app`, even a bug in app code cannot drop a table. This is also required for Row-Level Security to apply, because table owners bypass RLS.

## Local Postgres with Docker, without losing data

`largecom/compose.yaml`:

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: punta_dev
      POSTGRES_USER: punta_owner
      POSTGRES_PASSWORD: ${PG_PASSWORD}
    ports: ["5432:5432"]
    volumes:
      - punta_pgdata:/var/lib/postgresql/data
volumes:
  punta_pgdata:
    external: true   # created once by hand: docker volume create punta_pgdata
```

With `external: true`, `docker compose down -v` **will not** delete the volume.

If you already run Postgres another way (Postgres.app, Homebrew), skip Docker and just point `DB_URL` at it.

Spring Boot's `spring-boot-docker-compose` module can start and stop this file automatically. If you add it, keep `spring.docker.compose.lifecycle-management=start-only` so Spring never runs `down`.

## Tests

- `src/test/resources/application-test.properties` must **not** contain `spring.datasource.url`.
- Use `@ServiceConnection` with a `PostgreSQLContainer` (Testcontainers). Each test run gets a brand-new, disposable Postgres.
- Flyway runs the real migrations inside that container, so tests also prove the migrations work.

## Backups

| When | Command |
|:--|:--|
| Before running new migrations on a DB you care about | `pg_dump -Fc -d "$DB_URL" -f pre-migrate-$(date +%F-%H%M).dump` |
| Restore to a **new** DB to check it | `createdb punta_restore && pg_restore -d punta_restore file.dump` |
| Production | Managed provider's automatic daily backups + point-in-time recovery (Neon / Supabase / RDS) |
