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

## Amendment (2026-11-23): what the sandbox expires, and the authorized dataset works

Two statements above were written before the first apply, and the sandbox proved both wrong on 2026-11-16.

- **Expiration** (Context, and the second Consequence). The tables themselves do not expire. Their partitions do, by event date: 30 days in the raw tables (`raw_retention_days`) and 60 in the curated ones. The datasets' default expirations and the four views expire on a fixed date, 2027-01-15. A sample therefore leaves the raw tables 30 days after its events happened, not after its load, so the fixtures dated 2026-11-10 to 12 would leave the view empty by mid-December. Two changes follow: `data/scripts/run-local.sh --rebase-to-today` shifts a sample so its newest event lands on the day of the run, and `sandbox.sh prove` fails when `v_user_activity` comes back empty, instead of passing on nothing. The sample itself is now captured from Kafka (`data/scripts/seed-api.sh` and `capture-kafka.sh`, into the git-ignored `data/landing/`); the test fixtures stay synthetic, because they carry the rejected lines the quarantine tests need.
- **Authorized dataset** (fifth Consequence). It works between two sandbox projects: as `analyst-reader`, `prove` read `v_user_activity` and was refused on `curated_movements` and `raw_user_events`. Plan B (READER on `vortex_curated`) was not needed.

## Amendment (2026-11-23): out of the sandbox, with a budget and a kill-switch

Scenario B starts with billing, so the cost guardrails come first, before any paid job. Facts checked on 2026-11-23 against the Google Cloud documentation and the source of the Terraform provider (8.12):

- Linking billing does not clear the sandbox's expirations, and a dataset's default changes only new tables. The tables themselves never had an expiration of their own (`bq show`, 2026-11-23); the datasets' defaults, the curated partitions (60 days) and the four views (2027-01-15) did. No 60-day ceiling is documented for the sandbox: 60 days is what it writes.
- Cloud Billing has no spend cap for BigQuery, Dataflow or Cloud Storage, and cost data arrives hours late. A budget alert by e-mail cannot be sent on demand: the first one comes with the first real cost.
- During a Free Trial, credits offset the cost, so a budget that includes them never alerts.
- A new organization enforces the legacy `iam.allowedPolicyMemberDomains`, which takes no exception per principal and refuses `billing-budget-alert@system.gserviceaccount.com`, the account that publishes a budget to Pub/Sub. The managed `iam.managed.allowedPolicyMembers` takes a list of principals.
- The official kill-switch sample grants `roles/billing.admin` on the billing account; the documentation of what unlinking requires contradicts itself (project roles only, in one table; project and billing account roles, in the summary).

Decisions:

- **Billing:** a Free Trial billing account inside the organization, linked to both projects through `billing_account`. Dataproc Serverless starts at 12 vCPU, above the trial's 8, so its batch needs the upgrade to a paid account, which keeps the remaining credit.
- **Budget:** `budget_amount` (US$ 5) a month on both projects, alerts at 50, 90 and 100% of the gross cost (`EXCLUDE_ALL_CREDITS`), sent to a Cloud Monitoring e-mail channel; the billing account's IAM recipients are off. Amounts are in the account's currency.
- **Kill-switch:** the budget publishes to a topic in the ingestion project, and a Python Cloud Run function (`data/kill-switch`, one instance at most, no retries) unlinks billing from that project once `costAmount` reaches `kill_switch_amount` (US$ 3). It reads the cost, not `alertThresholdExceeded`, because notifications arrive whether or not a threshold was crossed. Its account holds Project Billing Manager and Browser on the ingestion project, with no role on the billing account. Linking billing back is a deliberate `gcloud billing projects link`, not an apply: after an unlink the provider keeps `billing_account` in the state, so the plan shows no drift, and with billing off Cloud Storage refuses the plan's refresh anyway.
- **Domain restriction:** on the ingestion project only, the legacy constraint allows all and the managed one allows the organization's principal set plus the budget's publisher.
- **Expirations:** cleared by the stack: the datasets' defaults become null, the curated partitions and the views get an explicit 0 (the provider keeps the old value when those are left out). The raw partitions keep `raw_retention_days`.
- **The e-mail is proven** by a temporary 1-unit budget at 1% on the ingestion project (`email_test_budget`), removed after the first e-mail.

Alternatives considered:

- **`roles/billing.admin` for the function, as in the sample.** It could also unlink every other project of the account or change its users.
- **`allow_all` on the ingestion project, without the managed constraint.** Any outside account could then get a role there.
- **Clearing the expirations with `bq update` only.** The state would keep 2027-01-15, and the next update of a view would write it back.
- **A threshold rule at a few cents on the real budget, to prove the e-mail.** It would wait for the first paid job instead of the first cent.

Consequences:

- The analytics project keeps billing after a cut; it runs only the analysts' queries, inside BigQuery's free tier.
- An unlinked project stops its paid services, and resources may be deleted if billing stays off for long. A cut is investigated and undone within days.

Results (2026-11-23):

- **The account is in BRL.** The console offered no other currency, so `budget_amount` is R$ 25 and `kill_switch_amount` R$ 15, about US$ 5 and US$ 3 at roughly R$ 5 to the dollar (an approximate rate, not a pinned one). In Brazil the Free Trial opens only after a prepayment, which stays as the account's balance; until then the account is closed.
- **The minimal roles are enough.** A simulated notification at R$ 16 unlinked the ingestion project about 15 seconds after it was published, with Project Billing Manager and Browser only; one at R$ 1 was logged and left billing on.
- **Terraform does not see the cut.** The plan reported no changes with the project unlinked, so billing went back with `gcloud billing projects link`; the expirations stayed cleared and `data/scripts/sandbox.sh prove` passed again.
- **The expirations are gone.** After the apply, `bq show` reads no default on the curated and analytics datasets, no expiration on the curated partitions and the four views, and 30 days on the raw partitions.
- **The apply needs retries on a new project.** The managed domain constraint took about three minutes to admit the budget's publisher; the Cloud Functions build reads the source from a bucket Google creates on the first deploy (`gcf-v2-sources-<number>-<region>`), so the builder's read grant is a project role with a condition on the two source buckets; and the Org Policy API, like the Budget API, refuses user credentials without a quota project.

## Amendment (2026-11-24): the policy tag on the raw personal data, and the budget e-mail

The raw dataset's access list was the only barrier (third Consequence), and whoever passes it reads every e-mail and IP address. Facts checked on 2026-11-24 against the documentation, the provider's source (8.11) and the raw tables:

- `attributes` holds personal data: in 4 of the 8 `user.logged_in` rows, `details.sessionId` repeats `session_id`. No row held the user's e-mail, IP address or username there, but `details` is an open map.
- Writing a tagged column needs no role on the tag, and an append keeps the tag. Reading it needs Fine-Grained Reader, which neither Owner nor Organization Administrator carries; an authorized view does not get around it.
- The provider sends an empty tag for every column without one in the code, so a tag set outside Terraform does not last.
- Deleting a policy tag, or its taxonomy, also takes it off the columns.

Decisions:

- **One tag, `pii`, on every personal column of the raw layer,** not only the e-mail and IP columns first planned: in `raw_user_events`, `user_id`, `username`, `user_email`, `ip_address`, `user_agent`, `session_id` and `attributes`; in `raw_movements`, `user_id`, `session_id` and `metadata`. `attributes` and `metadata` are open text from the producers, and any field added there would otherwise be readable.
- **One list,** `data/policy_tags.json`, read by Terraform and by the proof; a test requires every column the schemas call personal data, and both open-text columns, to be in it. `data/schemas/` does not change: the tag is added in Terraform.
- **One reader:** Fine-Grained Reader for `operator_member` only. `pipeline-launcher` writes and is refused.
- **The taxonomy** `vortex-pii` is in the datasets' region and enforces access control; it and the tag have `deletion_policy = "PREVENT"`. Nothing of this exists in the sandbox.

Alternatives considered:

- **Proving the tag as `analyst-reader`.** The dataset already refuses it, so a missing tag would go unnoticed. `pipeline-launcher` is the account that only the tag can refuse.
- **Dropping `details.sessionId` in the parser.** It would not cover the rows already loaded, nor the next field of an open map.
- **Data masking.** Nobody needs a masked raw column: analysts read the pseudonyms of the curated layer.

Consequences:

- Any job that reads a tagged column needs Fine-Grained Reader: `SELECT *`, a copy from the raw tables, a `MERGE` that matches on `user_id`. The Dataflow load only appends, so it needs none.

Results (2026-11-24):

- **The tag refuses.** As `pipeline-launcher`, each of the 10 columns is refused by the policy tag and the remaining ones are readable (`SELECT * EXCEPT`); the operator reads the 10. `COUNT(*)` passes and `COUNT(user_email)` is refused. Before the apply the same proof failed, with the 10 columns readable.
- **No drift.** The schema change was two in-place updates, and the next plan reported no changes. Tag and schema went in two applies, so the second plan could show each column.
- **An append keeps the tag.** After a load of the fixtures as `pipeline-launcher`, the proof passed again.
- **The budget e-mail is proven.** `1% of budget reached`, for the temporary budget, arrived from `CloudPlatform-noreply@google.com` on 2026-11-24 at 07:04; that budget was then removed.

## Amendment (2026-11-25): the infrastructure of the Dataflow job

Scenario B gets its network, buckets, secret and grants before its first job. Facts checked on 2026-11-25 against the documentation and the source of Beam 2.77 and of the provider (8.11):

- Enabling the Compute API creates a default network, with SSH and RDP open, unless `compute.skipDefaultNetworkCreation` is enforced. The documentation only promises that for new projects.
- With Dataflow Shuffle, the default for batch, one worker needs no ingress rule; a custom VPC only needs the subnetwork.
- Beam leaves the NDJSON of `FILE_LOADS` in the temporary bucket even after a successful job: the raw rows, personal data in clear.
- A bucket keeps deleted objects for seven days (soft delete) unless it is turned off.
- `dataflow.worker` carries `storage.objects.get` and `create`, but neither `list` nor `delete`. Staging calls `storage.buckets.get`, which no object role carries.

Decisions:

- **Network:** the constraint goes on the `vortex` folder, and the Compute API depends on it. The network `vortex-dataflow` has one /28 subnetwork in `us-central1` and no firewall rule.
- **Buckets:** landing (the events, and `quarantine/<run id>/`) deletes after `raw_retention_days`; temp deletes after one day. Soft delete and versioning are off on both.
- **Quarantine:** on Dataflow it goes to the landing bucket instead of staying local (Validation, above). It keeps the original line, so no person or account of ours reaches it besides the launcher, the worker and the project's owner.
- **Key:** the secret `vortex-hmac-key` holds `data/.env` as it is, key and version together, so a version cannot label the wrong key. The operator adds it; Terraform never sees the value.
- **Submitter:** `pipeline-launcher`, with `dataflow.developer`, actAs on `dataflow-worker` only, and objects in the two buckets.
- **Worker:** `dataflow.worker`, `bigquery.jobUser`, WRITER on the two datasets, the secret, objects in temp, read on landing and write under `quarantine/` only (an IAM condition).

Alternatives considered:

- **A quarantine table.** Its line column would need the policy tag, and the sink would write one more table for a handful of lines.
- **A new key in Secret Manager.** Every pseudonym would change, and one person would show up as two.
- **`dataflow.admin` for the launcher, as one documentation page asks.** It also carries storage permissions on the whole project.

Consequences:

- `dataflow.worker` is a project role, so the worker can also read and create objects in the other buckets of the project, the kill-switch's source included. Every job runs reviewed code, and it cannot overwrite or delete there.
- An object is gone when it is deleted: there is no undo on these buckets.

Results (2026-11-25):

- **The constraint works on an existing project.** Right after the apply that enabled the Compute API, `vortex-dataflow` was the only network, with no firewall rule.
- **No drift.** One apply for the constraint, one for the rest (21 created, 2 descriptions updated), no retry; the next plan reported no changes, and `sandbox.sh prove` passed.
- **The service agents came with the APIs.** Dataflow's and Compute's have their roles in the project without a grant of ours. Enabling Compute also gave Editor to the Google APIs Service Agent (`<number>@cloudservices.gserviceaccount.com`), run by Google; `iam.automaticIamGrantsForDefaultServiceAccounts` does not cover it, and Terraform does not see it because the grants are per member. The default Compute account was created with no role.

## References

- `data/pipeline/`: the pipeline, its tests and fixtures; `data/schemas/`: the table schemas, read by the tests and by Terraform; `data/policy_tags.json`: the tagged columns, read by both too.
- `data/scripts/`: key generation, the local run and the sandbox load and proof.
- `infra/terraform/gcp/data/`: the organization folder, the projects, datasets, tables, views and service accounts.
- `data/README.md`: how to run it.
- [ADR 0001](0001-ci-per-service-with-paths-filter.md): the CI pattern `data.yml` follows.
