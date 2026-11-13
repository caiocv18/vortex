# 3. PostgreSQL with Flyway for the cloud demo

- **Status:** Accepted
- **Date:** 2026-11-13
- **Pull requests:** #11 (the `postgres` profile), #12 (the `cloud` profile)

## Context

The main application knew two databases:

- H2 in memory for development and tests, with the schema created by Hibernate (`create-drop`) and seeded by `import.sql`;
- Oracle Database Free for the `prd` profile, run by Docker Compose with `mem_limit: 3g`, because the container needs more than the 2 GB that Oracle Free allows for the SGA and PGA.

The public demo is planned for an EC2 `t4g.small` with 2 GiB of RAM, inside a ceiling of US$ 10 a month in gross cost for the whole AWS account. Oracle does not fit that host, and H2 in memory loses every change on restart; the production checks also refuse an in-memory H2. The authorization service already runs on PostgreSQL 18.

## Decision

- A composable `postgres` profile (`application-postgres.properties`) switches the datasource to PostgreSQL and references nothing from Oracle. It reads `VORTEX_DB_URL`, `VORTEX_DB_USERNAME` and `VORTEX_DB_PASSWORD`.
- Flyway owns the schema, only in that profile:
    - `V1__create_inventory_schema.sql` creates the current model;
    - `V2__seed_demo_data.sql` loads the demo data once, skips rows whose parent is gone, and only moves a sequence forward;
    - Hibernate runs with `ddl-auto=validate`.
- H2 keeps `create-drop` and `import.sql` for development and tests, and the Oracle `prd` profile stays as it was.
- The cloud database is a database of its own, `vortex`, on the same PostgreSQL instance as the authorization service.
- Hardening is a separate `cloud` profile. The demo runs `SPRING_PROFILES_ACTIVE=cloud,postgres,sqs`, and a local PostgreSQL run is `dev,postgres`.

## Alternatives considered

- **Oracle Free on the demo host.** The compose limit alone (3 GB) is larger than the instance.
- **H2 in file mode.** No server to run and it survives a restart, but it is not the database the rest of the stack uses, and the schema would still come from Hibernate instead of reviewed migrations.
- **`ddl-auto=update`.** No migration to write, but the schema would change silently on boot and could not be reviewed.
- **A seed that runs on every boot** (a Flyway `afterMigrate` callback or `spring.sql.init`). Tried and dropped: on each restart it brought back demo rows that a visitor had deleted, without their effect on stock, and moved sequences backwards to `MAX(id)`. Spring Boot also discourages script initialization alongside Flyway.
- **A second PostgreSQL instance for the main application.** Cleaner isolation, but a second server process on a 2 GiB host for no gain at demo scale.

## Consequences

- The `vortex` database must exist before the first boot: Flyway creates schemas, not databases.
- The application tests need Docker. `PostgresProfileIntegrationTest` and `CloudProfileSecurityIntegrationTest` run against PostgreSQL 18.6 through Testcontainers and fail, rather than skip, without it.
- There are two sources of schema, Hibernate for H2 and Flyway for PostgreSQL. `validate` only checks tables and column types, so the PostgreSQL test drives a full create, read, update and delete over HTTP. Any entity change needs a new `V` migration.
- Profile order matters: later profiles win, so `postgres` must come after `dev`.
- The Oracle driver stays in the jar, since `prd` still uses it.

## References

- `backend/vortex-application-service/src/main/resources/application-postgres.properties` and `db/migration/postgres/`.
- `backend/vortex-application-service/src/main/resources/application-cloud.properties`.
- `backend/vortex-application-service/src/test/java/br/com/vortex/application/config/PostgresProfileIntegrationTest.java`.
- `infra/docker/docker-compose.yml:33` and `infra/docs/README-ORACLE.md:9`: the Oracle memory limit.
- `infra/terraform/README.md:3`: the monthly cost ceiling.
