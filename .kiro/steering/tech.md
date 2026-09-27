# Tech Stack

## Core Versions

| Technology | Version | Notes |
|---|---|---|
| Java | 25 | Required runtime and compile target |
| Spring Boot | 4.1.0 | Parent POM; do not mix with older Boot conventions |
| Vaadin Flow | 25.2.3 | Server-side Java UI only — no Hilla, no React views |
| jOOQ | 3.21.6 | Only data-access technology; JPA and Hibernate must not be used |
| PostgreSQL | 18 (image) | Only supported database; image pinned to `postgres:18` |
| Flyway | 13.0.0 | All schema changes delivered as versioned migration scripts |
| Spring Security | (Boot-managed) | BCrypt password encoding, work factor ≥ 10 |

## Key Libraries

| Library | Version | Purpose |
|---|---|---|
| `ch.martinelli.oss:jooq-spring` | 1.1.1 | jOOQ/Spring integration utilities |
| `ch.martinelli.oss:vaadin-jooq` | 2.1.3 | Vaadin data-binding ↔ jOOQ bridge |
| `ch.martinelli.oss:jooq-utilities` | 1.1.4 | `EqualsAndHashCodeJavaGenerator` for codegen |
| `org.jspecify:jspecify` | 1.0.0 | `@Nullable` annotations (provided scope) |
| `org.parttio:line-awesome` | 2.1.0 | Icon library for Vaadin views |

## Static Analysis (build-breaking)

| Tool | Version | Behavior |
|---|---|---|
| Error Prone | 2.50.0 | Compiler plugin; `failOnWarning=true` — any warning breaks the build |
| NullAway | 0.13.8 | Null-safety checks on `ai.unified.process.demo.book.library`; generated jOOQ code is excluded |
| spring-javaformat | 0.0.47 | Enforces Spring code style in the `validate` phase — code must be formatted before anything else runs |

Use JSpecify `@Nullable` on fields and parameters that genuinely may be null. Non-null is the default; do not annotate every field.

## Build Prerequisites

- **Docker** (or Testcontainers Cloud) is required for every build. The `generate-sources` phase spins up a `postgres:18` Testcontainer, runs Flyway migrations against it, then runs jOOQ codegen to produce sources under `target/generated-sources/jooq` in package `ai.unified.process.demo.book.library.db`.
- Generated jOOQ sources are added to the source root by `build-helper-maven-plugin`. Never edit them by hand.
- Never change the `db.image` property without also updating `TestcontainersConfiguration` — mismatched PostgreSQL versions produce subtle test failures.

## Build Commands

Commands are for Windows PowerShell (`.\mvnw`). In Command Prompt use `mvnw`; on macOS/Linux use `./mvnw`.

```powershell
.\mvnw compile                # start Postgres container, run Flyway, regenerate jOOQ sources
.\mvnw spring-boot:test-run   # run the app locally; TestcontainersConfiguration supplies Postgres
.\mvnw package                # produce executable JAR
.\mvnw test                   # browserless UI tests + ArchUnit checks
.\mvnw verify                 # unit + Playwright integration tests + JaCoCo merge
.\mvnw verify -Pcoverage      # full coverage report
```

Do **not** use `spring-boot:run` locally, even though it is the POM's default goal. It runs on the main classpath with no Testcontainers, so the app starts with no database.

## Database Migrations

- Every schema change must be a versioned Flyway script: `src/main/resources/db/migration/V<NNN>__<description>.sql`.
- Scripts run at build time (codegen) and at application startup (runtime). Both paths must work.
- Never modify an already-applied migration script. Add a new one instead.

## Testing Stack

### Unit / Browserless UI Tests (`*Test.java`)
- Extend `AbstractBrowserlessTest` → `SpringBrowserlessTest`.
- Full Spring context with a real Testcontainers PostgreSQL database.
- Use `@WithMockUser` for security context.
- Navigate and assert on Vaadin component trees without a browser.
- `DemoDataSeed` runs automatically, seeding 9 books and 4 loans designed to cover all UC-001 business rules.

### End-to-End / Playwright Tests (`*IT.java`)
- Extend `PlaywrightIT`.
- Full Spring Boot server on a random port; headless Chromium via Playwright 1.61.0 + Mopo 0.0.6.
- No mocking — sign in with the seeded demo accounts.
- Managed by `maven-failsafe-plugin`; run during `verify`.

### Architecture Tests
- `ArchitectureTest` (ArchUnit 1.4.2) contains three rules. See `structure.md` for which conventions are enforced and which are not.
- Runs as part of `.\mvnw test`; a violation breaks the build.

### Test Naming Conventions
- Browserless: `UC<NNN><UseCaseName>Test` (e.g. `UC001SearchCatalogTest`)
- Playwright E2E: `UC<NNN><UseCaseName>IT` (e.g. `UC001SearchCatalogIT`)
- Annotate test methods with `@UseCase(id = "UC-001", scenario = "...", businessRules = {"BR-XXX"})` to link tests back to use-case specs.

## Runtime Requirements

- Deployable as a single executable JAR. Requires only a JRE and a reachable PostgreSQL instance. Must start within 60 seconds.
- All production traffic must use TLS 1.2 or higher; plain HTTP must redirect to HTTPS.
- Sessions expire after 30 minutes of inactivity.
