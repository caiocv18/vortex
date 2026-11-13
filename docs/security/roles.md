# Roles and permissions

The main application (Spring Boot) authorizes every `/api/**` request with the roles the authorization service (Quarkus) returns when it validates the JWT. `JwtAuthenticationFilter` maps each role to a `ROLE_<name>` authority, and method security (`@PreAuthorize`) checks it on the controller.

While the authorization service is unreachable, `AuthServiceClient` falls back to checking the JWT locally (`AuthTokenVerifier`), and the roles then come from the token's `roles` claim as issued at login. A role removed in the database keeps applying in that degraded mode until the access token expires (15 minutes).

## Matrix

| Endpoint | VIEWER | USER | MANAGER | ADMIN |
|---|:---:|:---:|:---:|:---:|
| `GET /api/tipos-produto`, `/api/produtos`, `/api/movimentos` (list and `/{id}`) | yes | yes | yes | yes |
| `GET /api/relatorios/**` | yes | yes | yes | yes |
| `POST`, `PUT`, `DELETE` on `/api/tipos-produto`, `/api/produtos`, `/api/movimentos` | no | yes | yes | yes |
| `/api/queues/**` (queue monitoring and `POST /{nomeFila}/test-message`) | no | no | no | yes |
| Purchase order approval (planned, not implemented yet) | no | no | yes | no |

- No token, or a token the authorization service rejects: `401`.
- A valid token whose validation response carries no `roles`, or an empty list: the user is authenticated with no authority, so every endpoint above answers `403`. Until 2026-11 the filter assumed `USER` when the key was missing.
- `/health` is public. `/api/test/**`, Swagger and the H2 console stay outside this matrix and are still open in every profile; closing them in the planned `cloud` profile is a separate change.

## Decisions

- **Roles are listed explicitly, with no `RoleHierarchy`.** A hierarchy (ADMIN > MANAGER > USER > VIEWER) would let ADMIN approve purchase orders, which the matrix keeps for MANAGER: the agent proposes, a manager approves.
- **The CRUD controllers fail closed.** They carry `@CanWrite` at class level and each read handler opts into `@CanRead`, so a handler added without an annotation needs write access instead of opening up to VIEWER.
- **Public sign-up gets `USER`** (`Role.findUserRole()` in the authorization service). The roles come from the Flyway migration `V5__Seed_default_roles.sql`. Before it, only the `import.sql` seeded them, and Hibernate loads that file in tests only: outside tests `auth.roles` was empty and every sign-up got no role, which went unnoticed while the app checked no role. `V5` also gives `USER` to every existing user left without one. The planned public demo will hand out `VIEWER` instead, through a `cloud` profile that does not exist yet.

## Where it lives

- Annotations: `@CanRead`, `@CanWrite` and `@AdminOnly` in `backend/vortex-application-service/src/main/java/br/com/vortex/application/config/security/`.
- Guard test: `EndpointAuthorizationCoverageTest` encodes this matrix. It resolves each `/api/**` handler's effective `@PreAuthorize` (other than `/api/test/**`) with the scanner method security uses and evaluates it for every role. It fails when a handler has none, when an authenticated user with no role passes, when the route is not in the matrix, or when a role passes or is refused against the matrix. A new route needs a row here and in `expectedRoles()`.
- Behaviour test: `RoleAuthorizationIntegrationTest` drives the real filter chain over HTTP, with a mocked validation response per role, and proves every cell: `2xx` for each "yes" (including a full create, update and delete per writer role and resource) and `403` for each "no", plus `401` without a token.
- Error handling: `GlobalExceptionHandler` catches `Exception`, so it rethrows `AccessDeniedException` and `AuthenticationException`; without that, a role denial would come back as `500`.

## Trying other roles locally

Outside the authorization service's tests there are no seed users: the `admin`, `manager`, `user` and `viewer` accounts of its `import.sql` exist only in the H2 test database. A local account signs up as `USER`. To try another role, change its row in `auth.user_roles` in the auth database (role ids are in `V5__Seed_default_roles.sql`); the next request picks it up, because `validate-token` reads the roles from the database.
