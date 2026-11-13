# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common Commands

### Development Setup
```bash
# Quick start (recommended) - Starts all services (interactive)
./start-vortex.sh

# Lightest run (light mode, H2, no messaging, no prompts): ~650 MB for the whole system
./start-vortex.sh --modo leve -e dev -m none --no-interaction

# Hot reload (dev mode)
./start-vortex.sh -e dev -r dev -m none

# Manual setup - Main application only
cd backend/vortex-application-service && ./mvnw spring-boot:run
cd frontend/vortex-application-service && npm ci && npm run dev

# Complete setup with authentication - All services
cd backend/vortex-authorization-service && ./mvnw quarkus:dev
cd frontend/vortex-authorization-service && npm ci && npm run dev
cd backend/vortex-application-service && ./mvnw spring-boot:run
cd frontend/vortex-application-service && npm ci && npm run dev
```

Toolchain: Java 27 via SDKMAN (`sdk use java 27.0.1-tem`), Node.js 26 via nvm (`nvm use 26`), Docker Engine with Compose v2 (`docker compose`, never the v1 `docker-compose`). Both backends ship `./mvnw` (Maven 3.10.0), which checks the downloaded distribution against `distributionSha256Sum` in `.mvn/wrapper/maven-wrapper.properties`; bump the checksum together with the Maven version, or the wrapper refuses the download. The launcher loads SDKMAN and nvm itself, because `.bashrc` returns early in non-interactive shells.

### Build Commands
```bash
# Backend - Main Application Service (Spring Boot)
cd backend/vortex-application-service
./mvnw clean install          # Build project
./mvnw spring-boot:run        # Run development server
./mvnw spring-boot:build-image # Build Docker image

# Backend - Authorization Service (Quarkus)
cd backend/vortex-authorization-service
./mvnw clean install          # Build project
./mvnw quarkus:dev            # Run development server
./mvnw package                # Package application (target/quarkus-app/quarkus-run.jar)

# Frontend - Main Application (Vue.js)
cd frontend/vortex-application-service
npm run build                 # Production build
npm run type-check            # TypeScript validation
npm run lint                  # ESLint with auto-fix
npm run generate-api          # Regenerate API client from OpenAPI spec

# Frontend - Authorization (React)
cd frontend/vortex-authorization-service
npm run build                 # Production build
npm run lint                  # ESLint, no auto-fix, fails on any warning (lint:fix fixes)
npm run test                  # Vitest unit tests
```

### Testing Commands
```bash
# Backend - Main Application tests
cd backend/vortex-application-service
./mvnw test                   # All tests
./mvnw test -Dtest=*ControllerTest # Controller tests only
./mvnw verify                 # Tests + JaCoCo report + line coverage gate (what CI runs)
./mvnw verify -Djacoco.line.minimum=0.99 # Prove the gate fails

# Backend - Authorization Service tests
cd backend/vortex-authorization-service
./mvnw test                   # All tests

# Backend - Authorization Registration Flow tests (Specialized)
./scripts/run-auth-registration-tests.sh --all      # All registration tests (42 tests)
./scripts/run-auth-registration-tests.sh --unit     # Unit tests only (32 tests)
./scripts/run-auth-registration-tests.sh --integration # Integration tests only (10 tests)
./scripts/run-auth-registration-tests.sh --verbose  # Verbose output
./scripts/run-auth-registration-tests.sh --coverage # With JaCoCo coverage report (HTML, XML, CSV)
./scripts/run-auth-registration-tests.sh --quick    # Skip slow tests
./scripts/run-auth-registration-tests.sh --watch    # Watch mode
./scripts/run-auth-registration-tests.sh --ci       # CI optimized mode (coverage + reports)

# Backend - Authorization Password Recovery Flow tests (Specialized) ✅ 100% ESTÁVEIS
./scripts/run-auth-password-recovery-tests.sh --all        # All password recovery tests (56 tests)
./scripts/run-auth-password-recovery-tests.sh --unit       # Unit tests only (15 tests)
./scripts/run-auth-password-recovery-tests.sh --integration # Integration tests only (15 tests)
./scripts/run-auth-password-recovery-tests.sh --security   # Security tests only (8 tests)
./scripts/run-auth-password-recovery-tests.sh --validation # Validation tests only (18 tests)
./scripts/run-auth-password-recovery-tests.sh --verbose    # Verbose output
./scripts/run-auth-password-recovery-tests.sh --coverage   # With JaCoCo coverage report (HTML, XML, CSV)
./scripts/run-auth-password-recovery-tests.sh --quick      # Skip slow tests
./scripts/run-auth-password-recovery-tests.sh --watch      # Watch mode
./scripts/run-auth-password-recovery-tests.sh --ci         # CI optimized mode (coverage + reports)
# Note: Correções implementadas - Rate limiting, token invalidation, concurrent handling, timing attacks

# Frontend - Main Application tests
cd frontend/vortex-application-service
npm run test:unit             # Vitest unit tests
npm run test:e2e              # Playwright E2E tests

# Frontend - Authorization tests
cd frontend/vortex-authorization-service
npm run test                  # Vitest unit tests
npm run test:coverage         # Coverage report
```

### Environment Management
```bash
# Environment options
./start-vortex.sh -e dev      # Development (H2 database)
./start-vortex.sh -e prd      # Production (Oracle Database Free, main backend in Docker); needs a strong JWT_SECRET:
                              #   export JWT_SECRET="$(openssl rand -base64 48)"

# Run modes (-r/--modo, default leve; asked right after the environment)
./start-vortex.sh --modo leve # Jars + AOT cache, frontends built and served by one nginx
./start-vortex.sh --modo dev  # Hot reload: spring-boot:run, quarkus:dev, Vite dev servers

# Messaging systems
./start-vortex.sh -m kafka    # With Kafka messaging
./start-vortex.sh -m rabbitmq # With RabbitMQ messaging
./start-vortex.sh -m sqs      # With Amazon SQS
./start-vortex.sh -m none     # No messaging (note: --no-interaction defaults to kafka)

# Service control (the auth backend and auth frontend always start)
./start-vortex.sh --backend-only  # Backend only
./start-vortex.sh --frontend-only # Frontend only
./start-vortex.sh --stop          # Stop all services (pid files, TERM then KILL; compose down without -v)
./scripts/medir-memoria.sh        # Memory table: RSS per process/container, WSL VM, Windows free RAM
```

## Architecture Overview

### System Structure
- **Microservices Architecture**: 4 main services (2 backend + 2 frontend)
  - Main Application Service (Spring Boot - Port 8080)
  - Authorization Service (Quarkus - Port 8081)
  - Main Frontend (Vue.js - Port 5173/4173)
  - Auth Frontend (React - Port 3001)
- **Event-Driven**: Supports Kafka, RabbitMQ, and SQS messaging
- **Multi-Database**: 
  - H2 (development and tests) - Main application data, in memory
  - PostgreSQL (`postgres` profile) - Main application data in the cloud, own database `vortex`, schema by Flyway
  - Oracle (`prd` profile) - Main application data in the Docker production stack
  - PostgreSQL (auth service) - Authentication data
- **Type-Safe**: Auto-generated API clients from OpenAPI specifications
- **Security**: JWT-based authentication with refresh tokens; the main app authorizes by role (VIEWER, USER, MANAGER, ADMIN), matrix in `docs/security/roles.md`

### Backend Architecture

#### Main Application Service (Spring Boot 4.1 - Java 27)
- **Pattern**: Layered architecture (Controller → Service → Repository)
- **Location**: `backend/vortex-application-service/`
- **Key Packages**:
  - `controller/`: REST endpoints for inventory management
  - `service/`: Business logic and messaging integration
  - `repository/`: JPA data access
  - `dto/`: Data transfer objects
  - `model/`: JPA entities (Produto, TipoProduto, MovimentoEstoque)
  - `config/`: Configuration classes (CORS, messaging, OpenAPI, Security)
  - `factory/`: Message broker factory pattern
- **Authorization**: every `/api/**` handler (except `/api/test/**`) needs a `@PreAuthorize`, normally through `@CanRead`, `@CanWrite` or `@AdminOnly` in `config/security/`. The CRUD controllers carry `@CanWrite` on the class and `@CanRead` on each GET, so an unannotated new handler requires write access. `EndpointAuthorizationCoverageTest` encodes the matrix: it fails on a handler without `@PreAuthorize`, on one an authenticated user with no role passes, on a route missing from the matrix, and on any role passing or refused against it, so a new `/api` route needs a row in `docs/security/roles.md` and in its `expectedRoles()`. A validation response without `roles` grants no authority (403), never a default role. The roles themselves are seeded by the auth service's Flyway `V5__Seed_default_roles.sql`; its `import.sql` loads in tests only, so a role added there alone never reaches dev, leve or prd
- **Exception handling**: `GlobalExceptionHandler` catches `Exception`, so it rethrows `AccessDeniedException` and `AuthenticationException` for Spring Security to answer 403/401; removing that handler turns every role denial into a 500. In production the 500 carries only a generic message and the error id
- **Production and cloud**: `config/DeploymentProfiles` is the only place that decides them, with `Environment.matchesProfiles` (whole profile names, so `preprod` is not production). Production is `prd | prod | production | cloud`; `GlobalExceptionHandler` and `SecurityConfigurationValidator` use it, and the validator then fails the boot on a weak `JWT_SECRET` or an in-memory H2
- **Cloud profile** (`application-cloud.properties`, the public demo): Swagger, `/v3/api-docs` and the H2 console off, `TestController` absent (`@Profile("!cloud")`), and `SecurityConfig` switches to a chain that permits `/health` and the ERROR dispatch, requires a token on `/api/**`, and denies everything else, `/api/test/**` included. Without the ERROR dispatch rule a 403 from `@PreAuthorize` reaches the client as 401. The JWT filter skips `/swagger-ui*` and `/v3/api-docs`, so those answer 401 even with a token. X-Frame-Options stays `DENY` there (`sameOrigin` is for the H2 console elsewhere)
- **CORS**: one `UrlBasedCorsConfigurationSource` in `CorsConfig`, passed explicitly to the security chain (Spring MVC also publishes a `CorsConfigurationSource`, so autodetection would be ambiguous). Origins come from `vortex.cors.allowed-origins`: the local frontends in the base, `VORTEX_CLOUD_CORS_ORIGINS` in `cloud` (empty means no cross-origin access). Never name that variable `VORTEX_CORS_ALLOWED_ORIGINS`: relaxed binding makes it the key itself, so it would replace the list in every profile. No `@CrossOrigin` on controllers: with an empty global list, a handler-level one would open CORS on its own

#### Authorization Service (Quarkus 3.40 - Java 27)
- **Pattern**: RESTful microservice with reactive programming
- **Location**: `backend/vortex-authorization-service/`
- **Key Packages**:
  - `resource/`: JAX-RS REST endpoints for authentication
  - `service/`: Authentication business logic
  - `entity/`: User, Role, Token entities with Panache
  - `dto/`: Authentication request/response objects
  - `security/`: JWT service and password handling
  - `event/`: Event-driven authentication logging
  - `config/`: Messaging and security configuration
- **Testing**: 42 focused tests for registration flow with JaCoCo coverage
- **Quality Assurance**: Maven plugin with automated coverage reporting

### Frontend Architecture

#### Main Application (Vue.js 3.5 + Vuetify 4 + Vite 8 + TypeScript)
- **Pattern**: Component-based with centralized state management
- **Location**: `frontend/vortex-application-service/`
- **Key Directories**:
  - `views/`: Page-level components (Dashboard, Products, Reports)
  - `components/`: Reusable UI components with Vuetify
  - `stores/`: Pinia state management (auth, products, movements)
  - `api/generated/`: Auto-generated API clients from OpenAPI
  - `router/`: Vue Router with authentication guards
  - `utils/`: Authentication callback handling

#### Authorization Frontend (React 19 + Tailwind 4 + Vite 8 + TypeScript)
- **Pattern**: SPA with context-based state management
- **Location**: `frontend/vortex-authorization-service/`
- **Key Directories**:
  - `pages/`: Authentication pages (Login, Register, Reset)
  - `components/`: Reusable form components
  - `contexts/`: React Context for authentication state
  - `services/`: API integration with auth backend
  - `types/`: TypeScript type definitions

### Messaging Integration
- **Factory Pattern**: `MessageBrokerFactory` abstracts messaging systems
- **Services**: Separate producer/consumer services for each messaging system
- **Topics**: 
  - `vortex.movimento.estoque` - Inventory movements
  - `vortex.produto.events` - Product events
  - `vortex.alertas.estoque` - Stock alerts

### Configuration Management
- **Profile-Based**: Environment-specific configuration files
- **Key Files**:
  - `application.properties` - Base configuration (H2, `spring.flyway.enabled=false`)
  - `application-dev.properties` - Development environment
  - `application-prd.properties` - Production environment (Oracle)
  - `application-postgres.properties` - PostgreSQL datasource, composed with others (`dev,postgres` in the WSL, `cloud,postgres,sqs` on the EC2)
  - `application-cloud.properties` - Public demo hardening, no datasource keys (always together with `postgres`, which comes after it)
  - `application-kafka.properties` - Kafka configuration
  - `application-rabbitmq.properties` - RabbitMQ configuration
- **Profile order**: the last active profile wins a shared key, so `postgres` goes after `dev` (`postgres,dev` silently runs on H2). `PostgresProfilePropertiesTest` fails when `dev` sets a datasource, JPA, `sql.init`, cache or H2 key that `postgres` does not override

## API Structure

### Main Application Service (Port 8080)
- **Base URL**: `http://localhost:8080/api`
- **Documentation**: `http://localhost:8080/swagger-ui.html` (every profile except `cloud`)
- **OpenAPI Spec**: `docs/application/openapi-backend.json`, maintained by hand as the input of `npm run generate-api`. Its `servers` entry carries the `/api` base path and its operationIds were renamed for the client, so never overwrite it with an export of `/v3/api-docs` (the client's method names and `BASE_PATH` would change). Keep its `info` in step with `OpenApiConfig`

#### Key Endpoints
- **Products**: `/api/produtos` - Full CRUD operations
- **Product Types**: `/api/tipos-produto` - Category management
- **Movements**: `/api/movimentos` - Inventory transactions
- **Reports**: `/api/relatorios/*` - Business intelligence
- **Health**: `/health` - Application health check
- **Queue Monitoring**: `/api/queue` - Message broker status

### Authorization Service (Port 8081)
- **Base URL**: `http://localhost:8081/api/auth`
- **Documentation**: `http://localhost:8081/q/swagger-ui`
- **Health**: `http://localhost:8081/q/health`

#### Authentication Endpoints
- **Login**: `POST /api/auth/login` - User authentication
- **Register**: `POST /api/auth/register` - User registration
- **Logout**: `POST /api/auth/logout` - User logout
- **Refresh**: `POST /api/auth/refresh` - Token refresh
- **Password Reset**: `POST /api/auth/forgot-password` - Password recovery
- **Reset Password**: `POST /api/auth/reset-password` - Password reset

### Frontend API Integration
- **Auto-Generated**: API clients generated from OpenAPI spec
- **Command**: `npm run generate-api` - Regenerate after backend changes
- **Location**: `src/api/generated/` - TypeScript definitions and clients

## Development Workflow

### Local Development
1. **Main Backend**: Spring Boot on port 8080 with H2 database (or PostgreSQL with `SPRING_PROFILES_ACTIVE=dev,postgres` and `VORTEX_DB_URL`, `VORTEX_DB_USERNAME`, `VORTEX_DB_PASSWORD`)
2. **Auth Backend**: Quarkus on port 8081 with PostgreSQL 18 (`vortex-auth-db`, port 5432, volume `vortex-auth-db-data-pg18`)
3. **Main Frontend**: Vue.js on port 5173 (Vite dev server in dev mode, nginx `vortex-static` in light mode)
4. **Auth Frontend**: React on port 3001 (same split)
5. **Database Console**: `http://localhost:8080/h2-console` (dev environment)

### Production Deployment
1. **Backend Services**: Dockerized with Oracle (main) and PostgreSQL (auth)
2. **Frontend Applications**: Optimized builds served via Nginx
3. **Databases**: Oracle Database Free 23ai on port 1521 (service `FREEPDB1`, image `gvenzl/oracle-free`), PostgreSQL on port 5432
4. **Load Balancing**: Nginx reverse proxy for all services

### Testing Strategy
- **Unit Tests**: 
  - JUnit 5 for Spring Boot backend
  - Quarkus Test for authorization service (42 focused tests for registration flow)
  - Vitest for Vue.js frontend
  - Vitest for React auth frontend
- **Integration Tests**: 
  - Spring Boot Test with H2 database
  - Testcontainers PostgreSQL 18 (`postgres:18.6-alpine`, one container per test JVM in `PostgresContainerSupport`) for the `postgres` profile; it needs Docker and fails without it, never skips. It feeds the container through `VORTEX_DB_*`, not `@ServiceConnection`, so the profile's own driver and placeholders are what the test exercises
  - REST Assured for Quarkus service
  - Jakarta Bean Validation tests for DTO validation
- **E2E Tests**: Playwright covering complete user workflows
- **API Testing**: Dedicated script `scripts/test-queue-endpoint.sh`
- **Code Coverage**: 
  - JaCoCo plugin integrated with Maven (jacoco-maven-plugin 0.8.16) in both backends
  - Quality gates: auth 60% line and 50% branch coverage; main app 47% line coverage (`jacoco.line.minimum`)
  - Reports in HTML, XML, and CSV formats
  - Auth reports in `coverage-reports/auth-registration/` (local output of the scripts, ignored by git); main app reports in `backend/vortex-application-service/target/site/jacoco/`
- **CI**: GitHub Actions, one workflow per service (see Code Coverage & Quality Assurance)
- **Specialized Test Scripts**: 
  - `./scripts/run-auth-registration-tests.sh` - Focused on account creation flow (42 tests)
  - `./scripts/run-auth-password-recovery-tests.sh` - Focused on password recovery flow (56 tests)
  - Multiple execution modes: unit, integration, security, validation, coverage, watch, CI
  - Automatic JaCoCo integration via `--coverage` and `--ci` flags

## Key Development Notes

### Message Broker Integration
- Multiple messaging systems supported simultaneously
- Message broker selection via configuration profiles
- Factory pattern enables runtime switching between messaging systems

### Database Configuration
- **Development**: H2 in-memory database for rapid iteration
- **Cloud**: PostgreSQL through the `postgres` profile, in its own database `vortex` on the same instance as the auth service (Flyway never creates the database; create it before the first boot). The EC2 runs `SPRING_PROFILES_ACTIVE=cloud,postgres,sqs` and needs a strong `JWT_SECRET`, `VORTEX_DB_URL`, `VORTEX_DB_USERNAME`, `VORTEX_DB_PASSWORD`, and `VORTEX_CLOUD_CORS_ORIGINS` only if a browser calls the API from another origin. A non-empty list must also hold the demo's own public `https://` origin: behind the TLS-terminating nginx the backend sees `http` and treats the page's own writes as cross-origin (`server.forward-headers-strategy` is not set yet)
- **Production (Docker stack)**: Oracle with persistent storage and advanced features
- **Migrations**: 
  - Authorization service: Flyway, files in `src/main/resources/db/migration/`
  - Main application on H2: Hibernate `create-drop` plus `import.sql`; Flyway is off in the base profile
  - Main application on PostgreSQL: Flyway `db/migration/postgres/V1__create_inventory_schema.sql` (schema) and `V2__seed_demo_data.sql` (the `import.sql` demo data, applied once on a new database), then Hibernate `validate`. Never move the seed to an `afterMigrate` callback: it would run on every boot and bring back seeded movements the demo deleted without their stock effect. To restart the demo from scratch, recreate the database. The seed's `setval` only moves a sequence forward (backwards would reissue ids already used). `spring.jpa.defer-datasource-initialization=false` there, or `validate` may run before the tables exist. A model change needs a new `V<n>__*.sql`; `validate` checks tables, columns and types, never CHECK, UNIQUE or foreign keys
  - SQL files: SonarCloud reads `.sql` as PL/SQL, so no `NOT EXISTS`, `IF EXISTS` or `$$`

### Type Safety
- **Backend**: 
  - Strong typing with Java 27 (both services)
  - Bean validation annotations
  - OpenAPI 3.0 specification
- **Frontend**: 
  - TypeScript with auto-generated API clients
  - Vue.js 3 with Composition API
  - React 19 with hooks and strict typing
- **API Contract**: OpenAPI specification ensures consistency across services

### Launcher Script Features
- **Run modes**: `--modo leve` (default) runs both backends as jars (Spring Boot extracted to `target/leve/`, Quarkus `quarkus-run.jar`) with one JVM flag set defined at the top of `start-vortex.sh` (`-XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=192m`, `-Xmx320m` app, `-Xmx224m` auth, `MALLOC_ARENA_MAX=2`) and serves both `dist/` folders from one nginx (`infra/docker/docker-compose.leve.yml`, container `vortex-static`, ports 5173 and 3001, no `/api/` proxy). Measured: about 650 MB for the whole system (617–633 MB idle, 664 MB after signing in and opening the four main pages). `--modo dev` keeps hot reload with capped child JVMs.
- **Incremental builds (light mode)**: content stamps in `target/.vortex-build` and `dist/.vortex-build`; one build at a time, before any JVM starts. `.env.local` (theme) is rewritten only when its content changes.
- **AOT cache (light mode, JDK 27)**: trained once per build/flags/profiles (`target/leve/app.aot`, `target/quarkus-app/app.aot`), validated once with `-XX:AOTMode=on`, then used in auto mode. Startup: Spring 11.6 s to 5.8 s, Quarkus 4.7 s to 2.8 s. Never train both services at once (the training JVM forks a second JVM to assemble the cache, ~1.4 GB together for the Spring one).
- **Process control**: pid files in `logs/` for every process the launcher starts (JVMs, Maven wrappers, Vite servers, AOT training runs), written as `PID starttime boot_id` (field 22 of `/proc/PID/stat` and the kernel boot id). A pid is signalled only if it is still the same process (same start time, same boot, expected command line), so a PID reused after the distro restarts is never touched; legacy pid files holding only a PID are dropped without a signal. Stop sends TERM, waits up to 15 s, then KILL. Compose stacks go down without `-v` (with a neutral `JWT_SECRET`, since the prd compose files require it even for `down`). No broad `pkill` patterns.
- **AOT cache validation**: the cache is deleted only when the log shows the JVM rejected it (`Unable to use AOT cache.`); any other death during the `-XX:AOTMode=on` run keeps the cache and shows the real error.
- **Auth JWT keys**: before any build, the launcher makes sure `backend/vortex-authorization-service/src/main/resources/jwt/{privateKey,publicKey}.pem` exist (gitignored): it checks a full pair matches, moves a pair left in `META-INF/resources` (served as static files), derives a missing public key, or generates RSA 2048 with `openssl`. It never overwrites a key. The main backend gets `AUTH_JWT_PUBLIC_KEY_LOCATION` (and `AUTH_JWT_ISSUER`) in light and dev mode; the prd compose files mount the public key read-only at `/app/keys/auth-public-key.pem`.
- **prd secrets and wiring**: every prd compose file passes `JWT_SECRET` as `${JWT_SECRET:?...}` and the launcher rejects a missing or weak secret (same rules as `SecurityConfigurationValidator`) before touching anything. The prd backend joins `vortex-rabbitmq-network` and gets `RABBITMQ_*` for `-m rabbitmq`. The launcher fails fast when the `vortex-app` container exits during startup. Oracle has `mem_limit: 3g` (Oracle Free caps only SGA+PGA at 2 GB).
- **Auth PostgreSQL 18 volume**: `vortex-auth-db-data-pg18`. The old `docker_auth-db-data` holds a PostgreSQL 17 cluster the 18 image refuses; it is never deleted. `scripts/migrar-auth-db-pg18.sh` copies its data over (pg_dump from a temporary PG 17 on a copy of the old volume, single-transaction restore).
- **Launcher tests**: `bash scripts/test-start-vortex.sh` (no Docker, JVM or network; sources the launcher, whose `main` runs only when executed).
- **Ports**: `scripts/check-ports.sh --auto-kill` at startup checks 3001, 5173, 8080, 8081, 9092 and 5432, only listeners, skips container-published ports, TERM before KILL.
- **Logging**: Centralized logs in `/logs/` directory (`backend.log`, `auth-backend.log`, `build-*.log`, `aot-train-*.log`, `startup-times.log`)
- **Health Checks**: Validates service startup and readiness (`/health` and `/q/health`; there is no Actuator in the main backend)
- **Docker**: waits up to 60 s for the engine (it starts with the WSL distro boot); Compose v2 everywhere. All containers are named `vortex-*`.
- **WSL caveat**: the distro (and every JVM and container in it) shuts down when its last terminal closes.

### Authentication Flow
1. **User Access**: User visits main application (`http://localhost:5173`)
2. **Auth Check**: Router guard checks for valid JWT token
3. **Redirect**: If unauthenticated, redirects to auth frontend (`http://localhost:3001`)
4. **Login**: User authenticates via React auth interface
5. **Token Exchange**: Auth service returns JWT + refresh token
6. **Callback**: Redirects back to main app with authentication data
7. **Session**: Main app stores tokens and allows access to protected routes

### Documentation
- **Public docs are in English**: `README.md` and `README.pt-BR.md` mirror each other; change both in the same commit
- **Decisions**: `docs/adr/`, one numbered record per decision (Status, Date, Context, Decision, Alternatives considered, Consequences, References), indexed in `docs/adr/README.md`. Never rewrite an accepted record; supersede it with a new one
- **Screenshots**: `docs/screenshots/`, light theme, page only (no browser chrome), taken with a throwaway account
- **History**: `docs/archive/` keeps the early prompts, chats and reports as written, in Portuguese; link to it only as history
- **License**: Apache-2.0 (`LICENSE`), also declared in the main app's pom and in the OpenAPI `info`
- **Test credentials**: never in docs. The authorization service's test password survives only in the comments of `backend/vortex-authorization-service/src/main/resources/import.sql`, a file no profile loads, and in `SimpleAuthServiceTest`; the test-only `src/test/resources/import.sql` seeds the `USER` and `ADMIN` roles and no users

### Code Coverage & Quality Assurance

#### JaCoCo Integration
- **Plugin Version**: jacoco-maven-plugin 0.8.16 (`jacoco.version` in both poms; Spring Boot's BOM does not manage it)
- **Quality Gates**:
  - Auth: minimum 60% line coverage, 50% branch coverage (`BUNDLE`), set by the `jacoco.line.minimum` and `jacoco.branch.minimum` properties
  - Main app: minimum 47% line coverage (`BUNDLE`), set by the `jacoco.line.minimum` property from the 2026-11-13 baseline of 47.9% (847/1769 lines, after the cloud profile tests) rounded down (it started at 31% on 2026-11-09, then 43% after the role matrix tests and 44% after the PostgreSQL profile tests); override with `-Djacoco.line.minimum=...`. Raise it when coverage goes up, never lower it to make a build pass
- **Report Formats**: HTML (visual), XML (CI/CD), CSV (data analysis)
- **Location**: `coverage-reports/auth-registration/` and `coverage-reports/auth-password-recovery/` (auth scripts; local only, ignored by git, while CI keeps its reports as workflow artifacts); `target/site/jacoco/` (main app, `report` bound to the `test` phase, `check` to `verify`)
- **Agent wiring (main app)**: the empty `<argLine/>` property plus `@{argLine}` in the surefire config lets `prepare-agent` add the JaCoCo agent; keep `@{argLine}` when touching the surefire flags, or `jacoco.exec` is never written and `jacoco:check` silently skips

#### CI (GitHub Actions)
- **Workflows**: one file per service in `.github/workflows/`, each with a `changes` job and a build job named after the workflow:

  | Workflow | Build job runs when these change | What the build job does |
  |---|---|---|
  | `app-service.yml` | `backend/vortex-application-service/**` | Temurin 27, `./mvnw -B -ntp verify` (Testcontainers uses the runner's Docker), guard on `target/jacoco.exec`, coverage summary, `app-service-reports` artifact |
  | `auth-service.yml` | `backend/vortex-authorization-service/**` | Temurin 27, `./mvnw -B -ntp verify`, guard on `target/jacoco-quarkus.exec`, line and branch summary, `auth-service-reports` artifact (no Docker: H2 and every Dev Service off in `%test`, test-only keys in `src/test/resources/jwt-test`) |
  | `front-app.yml` | `frontend/vortex-application-service/**` | Node 26, `npm ci --ignore-scripts`, `npx eslint .` (never `npm run lint`, which has `--fix`), `npm run type-check`, `npx vitest run`, `npm run build-only` |
  | `front-auth.yml` | `frontend/vortex-authorization-service/**` | Node 26, `npm ci --ignore-scripts`, `npm run lint` (`--max-warnings 0`), `npm run type-check`, `npx vitest run`, `npx vite build` |
  | `launcher.yml` | any `*.sh` or `scripts/**` | `shellcheck --severity=error` and `bash -n` on every tracked `*.sh` except the generated `src/api/generated/git_push.sh` (the `mvnw` wrappers are not linted), a check that each of those is mode 100755 in git, then `bash scripts/test-start-vortex.sh` |

  Each workflow also triggers its build job when its own file changes. `npx vitest run` states the single run instead of relying on `CI=true` (the `test:unit` and `test` scripts call plain `vitest`, which watches outside CI). `--ignore-scripts` keeps dependency lifecycle scripts out of CI; only `@openapitools/openapi-generator-cli` (for `generate-api`) and the macOS-only `fsevents` declare one, and SonarCloud flags `npm ci` without it (`githubactions:S6505`). The Playwright suites are not in CI yet.
- **Executable bit**: the docs run every script as `./scripts/name.sh`. Editing a script from Windows (through `\\wsl.localhost`) rewrites the file as 0644; restore it with `chmod +x` before `git add`, or the `launcher` check fails
- **Check names**: `app-service`, `auth-service`, `front-app`, `front-auth` and `launcher`, plus one `changes` per workflow. Five checks share the name `changes`, so a ruleset must require only the build jobs
- **Coverage badge**: the `coverage-badge` job of `app-service.yml` runs only after a green build of a push to `master`. It sums the instructions of `jacoco.csv` from the `app-service-reports` artifact, draws `app-service/coverage.svg` in the shell (truncated to a tenth, shields.io colours) and pushes it with `app-service/source-sha` to the orphan `badges` branch. It is the only job with `contents: write`, so it runs no third-party action: only `actions/*` and its own shell may ever see that token. Before writing, it compares `source-sha` with its commit and skips when the badge already belongs to a newer one. The badge counts instructions (C0), not lines. It reports `skipped` on pull requests and must never be a required check; a ruleset must not protect the `badges` branch
- **Triggers**: `pull_request` and `push` to `master`, never `paths` in `on:` (a required check whose workflow never starts stays pending forever). The `changes` job runs `dorny/paths-filter` and the build job runs only when its folder or its own workflow file changed
- **Fail closed**: a skipped job reports success, so the build job's `if:` also runs it when `changes` did not succeed
- **Outputs (backends)**: the job summary table reads the report total (last `<counter>` of each type in `jacoco.xml`, the same number the gate compares; summing `jacoco.csv` double-counts lines shared with anonymous classes) and the floors from the pom properties; the reports artifact keeps the JaCoCo site and the surefire reports (plus the failsafe reports for the auth service) for 14 days
- **Local lint**: `actionlint` for the workflow files (it runs `shellcheck` on every `run:` block) and `shellcheck --severity=error` for the scripts
- **Conventions for new workflows**: third-party actions pinned by commit SHA with the version in a comment, `permissions: {}` at the top and minimal permissions per job, `shell: bash` (pipefail), action `path` inputs written from the repo root (`defaults.run.working-directory` applies to `run` steps only), concurrency per PR and per commit on `master`

#### Coverage Commands
```bash
# Generate coverage report via specialized script (Registration)
./scripts/run-auth-registration-tests.sh --coverage

# Generate coverage report via specialized script (Password Recovery)
./scripts/run-auth-password-recovery-tests.sh --coverage

# CI mode with coverage and reports  
./scripts/run-auth-registration-tests.sh --ci
./scripts/run-auth-password-recovery-tests.sh --ci

# Manual Maven execution
cd backend/vortex-authorization-service && ./mvnw test jacoco:report
cd backend/vortex-application-service && ./mvnw verify   # report + gate

# View HTML reports
open coverage-reports/auth-registration/index.html
open coverage-reports/auth-password-recovery/index.html
```

#### Coverage Configuration
- **Exclusions**: auth excludes DTOs, the main class and test utilities; the main app excludes nothing. Lombok (1.18.48) marks the code it generates with `@lombok.Generated` by default, and JaCoCo filters it, so no `lombok.config` is needed for that
- **Integration**: Automatic with test execution via Maven lifecycle
- **CI/CD Ready**: XML format for continuous integration pipelines
- **Quality Control**: Build fails if coverage thresholds not met

## Troubleshooting

### Common Issues

#### Flyway Migration Problems
**Problema**: `FlywayValidateException: Detected applied migration not resolved locally`

**Causa**: Uma migração foi aplicada ao banco anteriormente mas o arquivo de migração foi removido ou modificado.

**Soluções**:
1. **Para desenvolvimento**: Desabilitar validação do Flyway
   ```properties
   # Em application.properties
   %dev.quarkus.flyway.validate-on-migrate=false
   ```

2. **Para produção**: Executar Flyway repair ou recriar banco
   ```bash
   # Recrear banco PostgreSQL (desenvolvimento)
   docker stop vortex-auth-db && docker rm vortex-auth-db
   cd infra/docker && docker compose -f docker-compose.auth.yml up -d auth-db
   ```

3. **Criar migração faltante**: Se uma migração específica está faltando
   ```sql
   -- Exemplo: V4__Fix_missing_migration_history.sql
   -- No changes needed - this is just to satisfy Flyway validation
   SELECT 1;
   ```

#### Authorization Service Startup Issues
**Sintomas**: Serviço não inicia, erro de conexão com banco

**Verificações**:
1. PostgreSQL está rodando: `docker ps | grep vortex-auth-db`
2. Banco está acessível: `docker exec vortex-auth-db pg_isready -U vortex_auth`
3. Flyway migrations aplicadas: Verificar logs para erros de migração
4. Porta 8081 livre: `lsof -i :8081`

**Comandos úteis**:
```bash
# Verificar health do serviço
curl http://localhost:8081/q/health

# Logs do banco PostgreSQL
docker logs vortex-auth-db -f

# Iniciar apenas serviço de autorização
cd backend/vortex-authorization-service && ./mvnw quarkus:dev
```