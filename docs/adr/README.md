# Architecture decision records

Each record states one decision, the context that forced it, the alternatives that lost and what the decision costs. Records are never rewritten: a later decision supersedes an earlier one in a new record.

| # | Decision | Status | Date |
|---|---|---|---|
| [0001](0001-ci-per-service-with-paths-filter.md) | One CI workflow per service, gated by a paths filter | Accepted | 2026-11-13 |
| [0002](0002-explicit-role-matrix-without-hierarchy.md) | An explicit role matrix, without a role hierarchy | Accepted | 2026-11-13 |
| [0003](0003-postgres-with-flyway-for-the-cloud.md) | PostgreSQL with Flyway for the cloud demo | Accepted | 2026-11-13 |
| [0004](0004-multi-arch-images-on-ghcr.md) | Multi-arch images on GHCR, built without emulation and scanned by Trivy | Accepted | 2026-11-14 |

A new record takes the next number and the sections Status, Date, Context, Decision, Alternatives considered, Consequences and References.
