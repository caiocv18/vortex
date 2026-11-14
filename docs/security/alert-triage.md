# Security alert triage

Every code scanning alert (CodeQL and Trivy) and every Dependabot alert ends up here, either fixed or dismissed, with the reason and what would reopen it. A dismissal is never the answer to an alert that still has an open instance on `master` for a reason other than the one written below.

## v0.1.1 (2026-11-14)

Release [v0.1.0](https://github.com/caiocv18/vortex/releases/tag/v0.1.0) shipped with 16 open alerts. Version 0.1.1 fixes 12 of them and dismisses 4.

### Code scanning

| Alert | Tool | Rule or advisory | Where | Decision | Reason | Reopen when |
|---|---|---|---|---|---|---|
| #13 | CodeQL | `java/spring-disabled-csrf-protection` | `backend/vortex-application-service/.../SecurityConfig.java:43` | Dismissed: false positive | The API is stateless (`SessionCreationPolicy.STATELESS`) and reads credentials only from the `Authorization: Bearer` header. The application service neither sets nor reads a cookie, and both front ends send requests with `withCredentials: false`. CSRF needs a credential that the browser attaches on its own, and there is none. As defense in depth, since 0.1.1 the app's CORS never allows credentials (`CorsConfig`, proved by `CloudProfileSecurityIntegrationTest.corsNeverAllowsCredentials`) | The application service reads any credential from a cookie or a session, or its CORS allows credentials |
| #8, #9, #10 | CodeQL | `js/clear-text-storage-of-sensitive-data` | `frontend/vortex-application-service/src/stores/auth.ts:206`, `211`, `216` | Dismissed: won't fix | Accepted risk, described below. The same risk exists in the login app, which CodeQL does not flag | The fix below is scheduled, or the access token lifetime grows |
| #11, #12 | CodeQL | `js/insecure-randomness` | `frontend/vortex-application-service/e2e/auth.setup.ts:33`, `36` | Fixed | The throwaway test user's id, which is also part of its password, now comes from `randomUUID()` of `node:crypto` instead of `Math.random()` | |
| #2, #3, #4, #5 | Trivy | CVE-2026-63337, CVE-2026-69219, CVE-2026-69220, CVE-2026-75516 | `com.rabbitmq:amqp-client` 5.30.0 in the app image | Fixed | `rabbit-amqp-client.version` set to 5.37.1 in the app `pom.xml`, above the 5.30.0 that Spring Boot 4.1.2 manages | |
| #6, #7 | Trivy | CVE-2026-108106, CVE-2026-93451 | `org.xerial.snappy:snappy-java` 1.1.10.7 in the app image | Fixed | Pinned to 1.1.10.11 in `<dependencyManagement>`, the version Kafka 4.3.2 already uses | |
| #1 | Trivy | CVE-2026-106451 | `at.yawk.lz4:lz4-java` 1.11.2 in the app image | Fixed | Pinned to 1.11.4 in `<dependencyManagement>`, the version Kafka 4.3.2 already uses | |

The Trivy findings come from jars that the Spring Boot parent manages (`amqp-client`) or that `kafka-clients` 4.2.2 brings in (`snappy-java`, `lz4-java`). The dependency graph reads the poms statically and never sees them, so they raise no Dependabot alert ([ADR 0005](../adr/0005-master-ruleset-dependabot-and-immutable-releases.md)). RabbitMQ and Kafka are only enabled in the `rabbitmq` and `kafka` profiles, which the cloud deployment does not use, but the jars ship in the image, so they are fixed rather than dismissed.

### Dependabot

| Alert | Package | Advisory | Manifest | Decision | Reason |
|---|---|---|---|---|---|
| 3 | `minimatch` 10.2.6 | GHSA-g3hv-75wg-m249 | `frontend/vortex-application-service` | Fixed | `eslint` 10.15.0 and a scoped override `"minimatch@>=7.0.0 <11.0.8": "^11.0.8"` |
| 6 | `minimatch` 10.2.6 | GHSA-g3hv-75wg-m249 | `frontend/vortex-authorization-service` | Fixed | Same as alert 3 |
| 4 | `basic-ftp` 5.3.1 | GHSA-5pm8-86m8-9m6r | `frontend/vortex-application-service` | Fixed | Scoped override `"basic-ftp@<6.2.6": "^6.2.6"` |

All three are development dependencies. Dependabot could not open the fix itself: every patched version is a new major outside the range its parents declare (`@typescript-eslint/typescript-estree`, `glob` and `editorconfig` for `minimatch`; `get-uri` under `@openapitools/openapi-generator-cli` for `basic-ftp`), so its security update would have downgraded the parents. The overrides use a version range as the key, so they only replace the vulnerable range and leave other majors alone. `glob` 13 cannot run on `minimatch` 11 (it only treats a pattern part as magic when it is a `RegExp`, and `minimatch` 11 hands it a `Matcher`, so every wildcard would silently match nothing), so the inventory app also overrides `"glob@>=13 <14.0.6": "^14.0.6"` and moves `@openapitools/openapi-generator-cli` to 2.42.2, which already depends on `glob` 14. The key stops below 14.0.6 so that it also replaces the `glob` 14.0.4 that `@openapitools/openapi-generator-cli` pins exactly.

### Also fixed

Dependabot alerts 1 (`basic-ftp`, GHSA-c475-qrg2-pj4r), 2 (`glob`, GHSA-3g3m-2fjr-6pw4) and 5 (`basic-ftp`, GHSA-r4jh-29pc-gfqr) were auto-dismissed by GitHub's preset rule for development dependencies. The same overrides remove them from the lockfile.

The changes also close advisories that never showed up as alerts, either because the image scan reports only CRITICAL and HIGH or because they are published only in the package's own repository. Each fix goes past the first version that clears its alert, so it does not keep advisories the minimal fix would still carry:

- `amqp-client` 5.30.0: GHSA-qx7j-jv8m-fppr (medium, fixed in 5.31.0), GHSA-5m9f-rphj-c435 (medium) and GHSA-5xwg-cfvj-gff5 (low), both fixed in 5.33.0, and GHSA-h6w7-qmcm-q6xr, GHSA-7822-rcf6-97fx and GHSA-cqgh-8p3p-mx4m (medium, fixed in 5.35.0, 5.36.0 and 5.36.1). 5.34.0, the first version that clears the four HIGH ones, still has the last three.
- `minimatch`: GHSA-c859-jr93-m5j4 (medium, affects 7.4.0 to 11.0.7, so also the shipped 10.2.6; fixed in 11.0.8). Targeting 11.0.8 rather than 11.0.1, the first version that clears alerts 3 and 6, also keeps out GHSA-cf2h-pxjq-68xw (medium, 11.0.0 to 11.0.6) and GHSA-hqmv-qgp3-jx75 (medium, 11.0.3 to 11.0.7), which exist only in the 11.x line.
- `basic-ftp` 5.3.1: GHSA-5rfr-xx34-2xxv (medium, fixed in 6.2.2) and GHSA-2w5v-vj22-jp27 (medium, fixed in 6.2.6). 6.2.4, the first version that clears alert 4, still has the second.
- `glob`: GHSA-qxr8-q827-836c (low, affects 13.0.5 to 14.0.5, fixed in 14.0.6). Both the shipped 13.0.6 and the 14.0.4 that `@openapitools/openapi-generator-cli` 2.42.2 pins had it.

`npm audit` went from 18 findings to 0 in the inventory app and from 9 to 0 in the login app.

## Accepted risks

### Tokens in browser storage (alerts #8, #9 and #10)

The inventory app keeps the access token, the refresh token and the user profile in `localStorage`. The login app keeps only the two tokens there and holds the profile in memory. After sign-in, the login app (React) hands all three to the inventory app (Vue) in the URL fragment (`#authData=…`), which never reaches a server; the inventory app moves them into its own `localStorage` before the router starts. Any script that runs on either origin can read the tokens.

CodeQL flags only the inventory app: its source heuristic matches the function name `postToAuthService`. The login app stores the same two tokens (`AuthContext.tsx`, `services/api.ts`) with no alert, and the dismissal covers both.

Why not now: the real fix spans the authorization service and both front ends (a refresh token cookie, an in-memory access token, CORS with credentials and CSRF protection for the refresh endpoint), which is a feature of its own, not a dependency bump.

What limits the risk today:

- the access token lives 15 minutes, and logout revokes the refresh token on the server;
- neither app loads third-party scripts;
- planned with the cloud hosting: a Content-Security-Policy with `script-src 'self'`, report-only first and enforced once the API is live.

Known gaps, tracked as work items:

- the refresh token lives 7 days, is not rotated on refresh and is stored in clear in the database;
- the login app keeps its own copy of both tokens in `localStorage` from sign-in until its `/login` page loads again (every logout and every 401 in the inventory app ends there, and `LoginForm` clears them on mount);
- the inventory app still accepts the legacy `?authData=` query form, which would put tokens in server logs;
- the authorization service's CORS answers its allowed origins with `Access-Control-Allow-Credentials: true`, the Quarkus default when `quarkus.http.cors.origins` is set. It has no effect while that service sets no cookie, and the cookie work below sets it on purpose.

Planned fix: refresh token in an `HttpOnly`, `Secure`, `SameSite` cookie scoped to the authorization API, access token in memory only, refresh token rotation with reuse detection and hashed storage. The refresh endpoint is in the authorization service (Quarkus), so it then needs its own CSRF protection there (`SameSite` plus an `Origin` check), which no CodeQL rule covers. Alert #13 stays a false positive as long as the application service reads credentials only from the `Authorization: Bearer` header.

## How alerts are handled

- **Fix** when an upgrade, a pin or a code change removes the finding without changing behaviour. Pins that exist only to close an alert carry a comment saying when to drop them (see the Governance section of `CLAUDE.md`).
- **Dismiss** only with a row in this file and a short comment on the alert pointing here. Reasons follow GitHub's lists: `false positive`, `won't fix` or `used in tests` for code scanning; `tolerable_risk`, `inaccurate`, `not_used` or `no_bandwidth` for Dependabot.
- **New alerts** go through the same rules in the pull request that raises them, or right after the scheduled scan that finds them.
