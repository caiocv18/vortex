# Architecture decision records

Each record states one decision, the context that forced it, the alternatives that lost and what the decision costs. Records are never rewritten: a later decision supersedes an earlier one in a new record.

| # | Decision | Status | Date |
|---|---|---|---|
| [0001](0001-ci-per-service-with-paths-filter.md) | One CI workflow per service, gated by a paths filter | Accepted | 2026-11-13 |
| [0002](0002-explicit-role-matrix-without-hierarchy.md) | An explicit role matrix, without a role hierarchy | Accepted | 2026-11-13 |
| [0003](0003-postgres-with-flyway-for-the-cloud.md) | PostgreSQL with Flyway for the cloud demo | Accepted | 2026-11-13 |
| [0004](0004-multi-arch-images-on-ghcr.md) | Multi-arch images on GHCR, built without emulation and scanned by Trivy | Accepted | 2026-11-14 |
| [0005](0005-master-ruleset-dependabot-and-immutable-releases.md) | A master ruleset without bypass, Dependabot with a cooldown and immutable releases | Accepted | 2026-11-14 |
| [0006](0006-on-demand-demo-instance-behind-session-manager.md) | An on-demand demo instance, reached only through Session Manager | Accepted | 2026-11-14 |
| [0007](0007-static-spas-on-cloudfront.md) | Static single-page apps on S3 and CloudFront | Accepted | 2026-11-14 |
| [0008](0008-data-pipeline-on-google-cloud-with-pseudonymization.md) | A Beam pipeline into BigQuery, across two Google Cloud projects, with pseudonymization | Accepted | 2026-11-16 |

A new record takes the next number and the sections Status, Date, Context, Decision, Alternatives considered, Consequences and References.
