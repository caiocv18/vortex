# 8. A Beam pipeline into BigQuery, across two Google Cloud projects, with pseudonymization

- **Status:** Accepted
- **Date:** 2026-11-16

## Context

Vortex publishes events, but nothing reads them for analysis. The inventory API publishes a movement event each time stock goes in or out (topic `vortex.movimento.estoque`), and the authorization service can publish account events (`vortex-user-events`: sign-up, sign-in, sign-out, password and e-mail changes), off by default. The next module turns those events into tables an analyst can query, on Google Cloud, without exposing who did what.

Facts that shaped the design, as of 2026-11-16:

- The only personal data in the system lives in the authorization service: usernames, e-mail addresses, IP addresses and user agents. Its events carry all four. The movement event carries the user in `userId`, which the API still leaves empty; the JWT principal that will fill it is the username.
- The movement event's date-times are `LocalDateTime` (no offset); the account events' are `OffsetDateTime`. Jackson writes a `LocalDateTime` as an ISO string, or as an array when `WRITE_DATES_AS_TIMESTAMPS` is on.
- The account events carry secrets of their own: the ids of password reset and e-mail verification tokens.
- The whole portfolio has a ceiling of US$ 10/month. A Google Cloud project without billing runs in the BigQuery sandbox: no Cloud Storage, and every table and partition expires in 60 days. A local file can still be loaded, one file per job.
- `WriteToBigQuery` needs a `gs://` temporary location, even on the DirectRunner.
- An organization created after 2024-05-03 forbids service account keys by default.
- A truncating load (`WRITE_TRUNCATE`, `bq load --replace`) and `CREATE OR REPLACE` drop the column policy tags of the table; an append keeps them.
- `google_bigquery_dataset_iam_*` rewrites the dataset's access list and drops its authorized views and datasets.
- Dataproc Serverless starts at 12 vCPU (a 4-core driver and two 4-core executors). A Cloud Composer environment bills by the hour for as long as it exists.
- Apache Beam 2.77.0 supports Python 3.12 on the DirectRunner and on Dataflow.

## Decision

- **Engine:** Apache Beam in Python, pinned to `apache-beam[gcp]==2.77.0` on Python 3.12, managed with uv (`data/pipeline`). The same code runs on the DirectRunner today and on Dataflow later.
- **Projects:** a Cloud Identity Free organization with a `vortex` folder and two projects, created by the `infra/terraform/gcp/data` stack, whose state lives in the same S3 bucket as the AWS stacks:
  - **ingestion** holds `vortex_raw` and `vortex_curated`, and the service accounts that write them;
  - **analytics** holds `vortex_analytics`, which has views only, and pays for the analysts' queries.
- **Layers:**
  - `vortex_raw` keeps the events as received, personal data in clear, with partitions dropped after 30 days. A column policy tag on the e-mail and IP columns arrives with billing.
  - `vortex_curated` keeps the same events pseudonymized.
  - `vortex_analytics` has four views, authorized on `vortex_curated` as a dataset (`target_types = ["VIEWS"]`): `v_movements`, `v_user_events`, `v_user_activity` (movements and logins per pseudonymous user) and `v_daily_sales` (no user at all). Each view names its columns one by one, so a column added to a curated table stays out of reach until a view names it.
- **Pseudonymization:** HMAC-SHA256 with a 32-byte key over the trimmed, lowercased value, bound to a purpose (`user`, `email`), as 64 hex characters. The username becomes `user_key` in both tables, so movements and logins still join; the e-mail becomes `email_key`; the IP address becomes its /24 (IPv4) or /48 (IPv6) network. The user agent, the session id, the user's UUID and the producer's free text stay in the raw layer. Each curated row records `hmac_key_version`.
- **Allowlists:** the curated record is built from a column allowlist, and the event's extra fields from an allowlist per event type (roles, methods, reasons). Token ids are dropped while parsing, at any depth, so they reach no table at all.
- **Key:** read by the workers when they start (`DoFn.setup`), never passed as a pipeline option, which Dataflow shows in its console. Locally it lives in `data/.env`, git-ignored and written by a script that never prints it; on Dataflow it will come from Secret Manager.
- **Validation:** a line that can't become a row goes to `quarantine/rejected.ndjson` with the reason; it keeps the original line, so it stays local, next to the raw layer.
- **Time:** every timestamp is stored in UTC with microseconds. A value without an offset is read in `--source_timezone` (`America/Sao_Paulo` for the inventory API); nanoseconds are cut to microseconds.
- **Loads:** always `WRITE_APPEND` with `CREATE_NEVER`; Terraform owns the tables, with `deletion_protection`. A rerun appends the same events again, and the views keep the newest copy of each `event_id`.
- **Access:**
  - `pipeline-launcher` has WRITER on `vortex_raw` and `vortex_curated` and runs jobs in the ingestion project;
  - `analyst-reader` has READER on `vortex_analytics` and runs jobs in the analytics project;
  - `dataflow-worker` exists with no role until the Dataflow block;
  - the operator acts as either account through impersonation (Token Creator), never through a key.

  Dataset grants go through `google_bigquery_dataset_access` with basic roles, which the API keeps as written. Data Access audit logs are on for every service in both projects.
- **Scenario A (now, no billing):** the DirectRunner writes one NDJSON file per table; `data/scripts/sandbox.sh load` appends them as `pipeline-launcher`, and `sandbox.sh prove` reads `v_user_activity` as `analyst-reader` and fails if that account can read a base table. Every query carries `maximum_bytes_billed`.
- **Scenario B (with billing):** the events land in a bucket, and Dataflow runs one `n1-standard-1` worker with a public IP, no autoscaling and `max_workflow_runtime_walltime_seconds=1800`, writing through `FILE_LOADS`. A US$ 5 budget publishes to a kill-switch that unlinks billing from the ingestion project.
- **CI:** `data.yml`, in the pattern of ADR 0001: `uv sync --locked`, Ruff, and pytest with branch coverage on the DirectRunner.

## Alternatives considered

- **Dataproc and Spark as the main engine.** Dataproc Serverless starts at 12 vCPU per batch, and the volume is a few thousand events. Dataflow with one worker costs cents per run and nothing between runs. Dataproc stays as one short batch, to compare.
- **Cloud Composer for orchestration.** An environment bills every hour it exists, far above the ceiling. Airflow runs locally in Docker with the Beam and Google providers.
- **A plain SHA-256 of the username.** Usernames are easy to guess, so a dictionary rebuilds the mapping. With HMAC, nobody without the key can.
- **Reversible tokenization (Cloud DLP or deterministic AEAD).** Nobody needs to get the username back from the analytics side, DLP is billed per gigabyte, and a reversible token is one more secret to guard.
- **One project with column-level security only.** The analyst would sit in the project that holds the raw data. Two projects keep separate IAM and billing boundaries, and the views are the only bridge.
- **`WriteToBigQuery` in scenario A.** It needs a `gs://` temporary location, and the sandbox has no Cloud Storage.
- **Service account keys.** Forbidden by the organization's baseline, and a key file is a long-lived secret on disk. Impersonation gives short-lived tokens and an audit trail.
- **Streaming from Pub/Sub.** It runs, and costs, all the time. A batch over landing files covers the analysis, which looks back over days.
- **The kill-switch on the analytics project.** That project holds views only; the ingestion project is where Dataflow spends.

## Consequences

- Under the LGPD, pseudonymized data is still personal data: whoever holds the key can link a pseudonym back to a person. The key is guarded like a password. Rotating it changes every pseudonym, and `hmac_key_version` tells which key produced each row.
- While both projects stay in the sandbox, every table and partition expires in 60 days. The next apply recreates the tables, and the runs are reloaded.
- Until billing allows the policy tag, the raw dataset's access list is the only barrier: just `pipeline-launcher` writes it, and no analyst account can read it.
- Any view created in `vortex_analytics` can read `vortex_curated`. Only Terraform creates views there; `analyst-reader` has READER only.
- An authorized dataset between two sandbox projects is not proven yet. If the sandbox refuses it, plan B is READER on `vortex_curated` for `analyst-reader` until billing; `sandbox.sh prove` then fails on purpose, because the analyst can read a base table.
- Reruns duplicate rows in storage; the views hide them.
- The killed Dataflow job of scenario B leaves temporary files behind, so the loads must stay idempotent and the temporary bucket has a lifecycle rule.
- Cost: nothing in scenario A (the sandbox gives 10 GB of storage and 1 TB of queries a month). Scenario B is capped by the budget and the kill-switch.

## References

- `data/pipeline/`: the pipeline, its tests and fixtures; `data/schemas/`: the table schemas, read by the tests and by Terraform.
- `data/scripts/`: key generation, the local run and the sandbox load and proof.
- `infra/terraform/gcp/data/`: the organization folder, the projects, datasets, tables, views and service accounts.
- `data/README.md`: how to run it.
- [ADR 0001](0001-ci-per-service-with-paths-filter.md): the CI pattern `data.yml` follows.
