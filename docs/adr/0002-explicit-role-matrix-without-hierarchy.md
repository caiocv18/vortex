# 2. An explicit role matrix, without a role hierarchy

- **Status:** Accepted
- **Date:** 2026-11-13
- **Pull request:** #10

## Context

The authorization service issues four roles: `VIEWER`, `USER`, `MANAGER` and `ADMIN`. Until this decision the main application checked no role at all: any valid token could create, change or delete stock, and the JWT filter assumed `USER` when the validation response carried no roles.

The roles do not form a line. A planned feature, purchase order approval, belongs to `MANAGER` alone: an agent proposes an order, a manager approves it, and an administrator manages the system without approving purchases.

## Decision

- Each endpoint names the roles it accepts, through three meta-annotations over `@PreAuthorize`:
    - `@CanRead`: `VIEWER`, `USER`, `MANAGER` and `ADMIN`;
    - `@CanWrite`: `USER`, `MANAGER` and `ADMIN`;
    - `@AdminOnly`: `ADMIN`.
- No `RoleHierarchy`.
- The CRUD controllers fail closed: `@CanWrite` sits at class level and each read handler opts into `@CanRead`, so a handler added without an annotation needs write access.
- A token whose validation response has no roles authenticates with no authority, so every endpoint answers `403`.
- The matrix lives in `docs/security/roles.md`, and a test encodes it.

## Alternatives considered

- **A hierarchy (`ADMIN > MANAGER > USER > VIEWER`).** Shorter annotations, but `ADMIN` would inherit purchase order approval, which the matrix keeps for `MANAGER`. Excluding it would need an exception to the hierarchy, which is the explicit list again.
- **URL rules in the security configuration.** One place for every rule, but the rule drifts away from the handler it protects, and a new route is open until someone adds a pattern.
- **Fine-grained permissions (authorities per action).** More flexible, and more than four roles need.

## Consequences

- A new route needs a row in `docs/security/roles.md` and in `expectedRoles()` of `EndpointAuthorizationCoverageTest`; the test fails until both agree.
- `RoleAuthorizationIntegrationTest` drives the real filter chain and proves every cell: `2xx` for each "yes", `403` for each "no", `401` without a token.
- Adding a role means revisiting each annotation, which is cheap with three of them.
- `GlobalExceptionHandler` must rethrow `AccessDeniedException` and `AuthenticationException`, or a denial comes back as `500`.
- The roles must exist in every environment: the Flyway migration `V5__Seed_default_roles.sql` seeds them, since before it only the test-only `import.sql` did.

## References

- `docs/security/roles.md`: the matrix and its decisions.
- `backend/vortex-application-service/src/main/java/br/com/vortex/application/config/security/CanRead.java`, `CanWrite.java` and `AdminOnly.java`.
- `backend/vortex-application-service/src/test/java/br/com/vortex/application/config/security/EndpointAuthorizationCoverageTest.java:74`: `expectedRoles()`.
- `backend/vortex-authorization-service/src/main/resources/db/migration/V5__Seed_default_roles.sql`.
