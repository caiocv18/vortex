# Roles and permissions

The main application (Spring Boot) authorizes every `/api/**` request with the roles the authorization service (Quarkus) returns when it validates the JWT. `JwtAuthenticationFilter` maps each role to a `ROLE_<name>` authority, and method security (`@PreAuthorize`) checks it on the controller.

## Matrix

| Endpoint | VIEWER | USER | MANAGER | ADMIN |
|---|:---:|:---:|:---:|:---:|
| `GET /api/tipos-produto`, `/api/produtos`, `/api/movimentos` (list and `/{id}`) | yes | yes | yes | yes |
| `GET /api/relatorios/**` | yes | yes | yes | yes |
| `POST`, `PUT`, `DELETE` on `/api/tipos-produto`, `/api/produtos`, `/api/movimentos` | no | yes | yes | yes |
| `/api/queues/**` (queue monitoring and `POST /{nomeFila}/test-message`) | no | no | no | yes |
| Purchase order approval (planned, not implemented yet) | no | no | yes | no |

- No token, or a token the authorization service rejects: `401`.
- A valid token whose validation response carries no `roles`: the user is authenticated with no authority, so every endpoint above answers `403`. Until 2026-11 the filter assumed `USER` in that case, which granted write access.
- `/health` is public. `/api/test/**`, Swagger and the H2 console stay outside this matrix and are still open in every profile; closing them in the planned `cloud` profile is a separate change.

## Decisions

- **Roles are listed explicitly, with no `RoleHierarchy`.** A hierarchy (ADMIN > MANAGER > USER > VIEWER) would let ADMIN approve purchase orders, which the matrix keeps for MANAGER: the agent proposes, a manager approves.
- **The CRUD controllers fail closed.** They carry `@CanWrite` at class level and each read handler opts into `@CanRead`, so a handler added without an annotation needs write access instead of opening up to VIEWER.
- **Public sign-up gets `USER`** (`Role.findUserRole()` in the authorization service). The planned public demo will hand out `VIEWER` instead, through a `cloud` profile that does not exist yet.

## Where it lives

- Annotations: `@CanRead`, `@CanWrite` and `@AdminOnly` in `backend/vortex-application-service/src/main/java/br/com/vortex/application/config/security/`.
- Guard test: `EndpointAuthorizationCoverageTest` resolves each `/api/**` handler's effective `@PreAuthorize` (other than `/api/test/**`) with the scanner method security uses and evaluates it. It fails when a handler has none, when an authenticated user with no role passes, or when a handler that is not `GET`, `HEAD` or `OPTIONS` admits `VIEWER`.
- Behaviour test: `RoleAuthorizationIntegrationTest` drives the real filter chain over HTTP with a mocked validation response per role and checks `401`, `403` and `2xx`.
- Error handling: `GlobalExceptionHandler` catches `Exception`, so it rethrows `AccessDeniedException` and `AuthenticationException`; without that, a role denial would come back as `500`.

The seed users in the authorization service's `import.sql` (`backend/vortex-authorization-service/src/main/resources/import.sql`) cover each role: `admin` (ADMIN and USER), `manager` (MANAGER and USER), `user` (USER) and `viewer` (VIEWER).
