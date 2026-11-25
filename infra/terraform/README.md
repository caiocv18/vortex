# Terraform

Infrastructure as code for the AWS account that hosts the Vortex portfolio, and for the Google Cloud projects of its data pipeline. The AWS account runs on the AWS Free plan, and the whole project has a ceiling of **US$ 10/month** in gross cost.

| Stack | State key | What it owns |
|---|---|---|
| `bootstrap/` | `bootstrap/terraform.tfstate` | The S3 bucket that stores every stack's state, its own included |
| `foundation/` | `foundation/terraform.tfstate` | The public Route 53 zone of the portfolio domain and the gross monthly cost budget |
| `demo/` | `demo/terraform.tfstate` | The on-demand demo instance, reached only through Session Manager, with its network, role and data volume ([ADR 0006](../../docs/adr/0006-on-demand-demo-instance-behind-session-manager.md)) |
| `site/` | `site/terraform.tfstate` | The two single-page apps on private S3 buckets behind CloudFront, with their certificate, headers policy, routing function and DNS records ([ADR 0007](../../docs/adr/0007-static-spas-on-cloudfront.md)) |
| `gcp/data/` | `gcp/data/terraform.tfstate` | On Google Cloud: a folder with the ingestion and analytics projects, the BigQuery datasets, tables and views of the data pipeline, and its service accounts ([ADR 0008](../../docs/adr/0008-data-pipeline-on-google-cloud-with-pseudonymization.md)) |

A later stack gets its own folder and state key. Google Cloud stacks live under `gcp/` and keep their state in the same bucket.

## Decisions

- **State:** S3 with the native lock file (`use_lockfile = true`), no DynamoDB table. Versioning keeps old states for 90 days, so a bad apply can be rolled back.
- **State bucket name:** in the account regional namespace (`-an` suffix). Only this account can ever create that name, so it can't be squatted or re-created by someone else.
- **Encryption:** SSE-S3 (AES256). A customer managed KMS key would cost US$ 1/month and add nothing for a single-owner account.
- **Budget:** counts **gross** cost (`include_credit = false`). With credits included, the Free plan credits net every charge to zero and no alert fires until the credits are gone.
- **Zone:** created here and delegated at the registrar. The zone that domain registration creates on its own is deleted, so there is only one.
- **No account id or email in the repository:** the bucket name is derived from the caller identity, the backend settings live in `backend.hcl` and the alert addresses in `terraform.tfvars`, both git-ignored.
- **Credentials** come from the environment, never from the code: `AWS_PROFILE` locally (an `aws login` session works with Terraform 1.15+), OIDC in CI later.
- `prevent_destroy` guards the state bucket and the zone: losing either breaks everything that depends on it.

## Prerequisites

- Terraform 1.17+, AWS CLI 2.32+ (for `aws login`), tflint and checkov.
- A profile that can manage S3, Route 53 and Budgets (plus EC2, IAM and Systems Manager for the demo, and CloudFront and ACM for the site): run `aws login --profile <profile>`, then `export AWS_PROFILE=<profile>`.

## First run (once per account)

The bootstrap stack creates the bucket its own state will live in, so its first apply uses a local backend. The override file and `../backend.hcl` are git-ignored. The second `init` moves the state into the bucket, and the local copy is deleted only after the migrated state is confirmed to hold the bucket.

```sh
cd infra/terraform/bootstrap
printf 'terraform {\n  backend "local" {}\n}\n' > local_backend_override.tf
terraform init
terraform apply
terraform output -raw backend_hcl > ../backend.hcl
rm local_backend_override.tf
terraform init -migrate-state -backend-config=../backend.hcl
terraform state list | grep -qx 'aws_s3_bucket.state' && rm -f terraform.tfstate terraform.tfstate.backup
```

Then the foundation stack. Put `budget_alert_emails` in `terraform.tfvars`; the last command prints the name servers to set at the registrar.

```sh
cd ../foundation
cp terraform.tfvars.example terraform.tfvars
terraform init -backend-config=../backend.hcl
terraform apply
terraform output name_servers
```

Right after the apply, and within 12 hours:

1. **Verify the alert addresses.** AWS Budgets only notifies verified addresses, and Terraform can't see the status. Signed in to this account, open Billing and Cost Management > Budgets > `portfolio-gross-monthly` and make sure every recipient shows *Active*; use *Resend verification* (Pending) or *Send notification* (Inactive) until it does. Repeat whenever `budget_alert_emails` changes.
2. **Delegate the domain.** Set the domain's name servers to the four printed above.
3. **Delete the duplicate zone** if the domain was registered in Route 53: registration creates a second zone with the same name (comment "HostedZone created by Route53 Registrar"). Delete that one, never the zone whose id is `terraform output -raw zone_id`; a deletion in the console ignores `prevent_destroy`. Deleted within 12 hours of creation, it costs nothing.

A domain registered or renewed in Route 53 is a gross cost of that month, so the 50% and 100% alerts fire on the fee alone. The 300% alert still catches real spend on top of it; check the month in Cost Explorer with the Registrar service filtered out.

## Day to day

```sh
terraform -chdir=infra/terraform/foundation init -backend-config=../backend.hcl
terraform -chdir=infra/terraform/foundation plan
```

## Demo

The demo instance exists only while `demo_enabled` is `true`. The network, the instance role and the data volume stay when it is off, so turning it on again mounts the same data. Always apply a saved plan that was read first: `aws_ebs_volume.data` must never show up as destroyed or replaced.

The off plan still prints the data volume's id twice, and both are expected: inside the destroyed attachment, and inside the destroyed instance's `ebs_block_device`, with `delete_on_termination = false`. The provider reads every attached volume into that block, but terminating the instance leaves it alone. To check a saved plan without reading it all:

```sh
terraform show -json off.tfplan \
  | jq -r '.resource_changes[] | select(.change.actions != ["no-op"]) | "\(.address) \(.change.actions)"'
```

It must list only `aws_volume_attachment.data[0]` and `aws_instance.demo[0]`. In an on plan, both are creates, and the attachment's `volume_id` matches `terraform output -raw data_volume_id`.

```sh
cd infra/terraform/demo
terraform init -backend-config=../backend.hcl

# On: a new instance from the latest Amazon Linux 2023 arm64 image
terraform plan -var demo_enabled=true -out=on.tfplan
terraform apply on.tfplan

# Off: terminates the instance and keeps the data volume
terraform plan -var demo_enabled=false -out=off.tfplan
terraform apply off.tfplan
```

There is no SSH. A shell on the instance goes through Session Manager, which needs the [session-manager-plugin](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html) next to the AWS CLI:

```sh
aws ssm start-session --target "$(terraform output -raw instance_id)"
```

A script without a terminal can run one command through `--document-name AWS-StartNonInteractiveCommand`. The plugin then needs a stdin that stays open, such as `< <(sleep 600)`; with a closed one it stops at `Cannot perform start session: EOF`.

The first boot runs `infra/cloud/user-data.sh` and logs to `/var/log/vortex-user-data.log`. A changed user data or a newer image applies on the next launch only (`ignore_changes`), so turning the demo off and on is how either reaches the instance.

## Site

The inventory app answers at the apex domain and the login app at `login.<domain>`, each from a private bucket behind its own CloudFront distribution. The stack adds records to the foundation's zone, so the foundation comes first.

```sh
cd infra/terraform/site
terraform init -backend-config=../backend.hcl
terraform plan -out=site.tfplan
terraform apply site.tfplan
```

The first apply waits until the certificate is issued and both distributions are deployed, which takes 5 to 20 minutes.

The buckets start empty. `scripts/publicar-fronts.sh` builds both apps from the committed `HEAD` with Node.js 26 as in CI (24.x from 24.15 and 22.x from 22.22.2 also meet the dependencies), writes the stack's URLs into each build, uploads them and invalidates `/index.html`. Pass `app` or `login` to publish one site only. From the repository root:

```sh
AWS_PROFILE=<profile> scripts/publicar-fronts.sh
```

Routes such as `/products` or `/reset-password/<token>` reach `index.html` through the `vortex-spa-rewrite` function. Its rules are in `site/spa-rewrite.js`; any change to them goes through a plan like the rest of the stack.

Terraform does not delete a bucket that still holds objects, so a `terraform destroy` of this stack needs both buckets emptied first:

```sh
aws s3 rm "s3://$(terraform -chdir=infra/terraform/site output -raw app_bucket)" --recursive
aws s3 rm "s3://$(terraform -chdir=infra/terraform/site output -raw login_bucket)" --recursive
```

## Google Cloud data

The `gcp/data` stack creates a `vortex` folder in the Google Cloud organization, an ingestion and an analytics project, the BigQuery datasets, tables and views of the data pipeline, and its service accounts ([ADR 0008](../../docs/adr/0008-data-pipeline-on-google-cloud-with-pseudonymization.md)). Its state goes to the same S3 bucket, so it needs the AWS profile as well as Google credentials.

Prerequisites:

- A Cloud Identity organization with a verified domain. The verification TXT value goes into `apex_txt_records` in the foundation's `terraform.tfvars`, because Route 53 keeps a single TXT record set per name.
- The Google Cloud CLI, and an operator account with Organization Administrator, Folder Admin and Project Creator on the organization, signed in with `gcloud auth login` and `gcloud auth application-default login`. The organization forbids service account keys: Terraform runs with the operator's Application Default Credentials.
- `org_id` and `operator_member` in `gcp/data/terraform.tfvars` (git-ignored).
- To leave the sandbox: a billing account in the organization and Organization Policy Administrator for the operator (the domain exception below). A new organization grants Billing Account Creator to the whole domain; this one had it removed, so the operator grants it to itself only to create the account, then removes it. `billing_account` and `alert_email` go into `terraform.tfvars`.

```sh
cd infra/terraform/gcp/data
cp terraform.tfvars.example terraform.tfvars
terraform init -backend-config=../../backend.hcl
terraform plan -out=data.tfplan
terraform apply data.tfplan
gcloud auth application-default set-quota-project "$(terraform output -raw ingest_project_id)"
```

The last command gives the operator's credentials a quota project, which the IAM Credentials API requires when `data/scripts/sandbox.sh` impersonates the service accounts.

Without `billing_account`, both projects stay in the BigQuery sandbox. The tables do not expire, but their partitions do, by event date: 30 days in the raw tables (`raw_retention_days`) and 60 in the curated ones; the datasets' default expirations and the views expire on a fixed date (2027-01-15 for the first apply). The datasets declare those expirations, so the plan shows no drift. A sample whose events are older than the retention leaves the views empty, and `data/scripts/sandbox.sh prove` then fails; reload it with `data/scripts/run-local.sh --rebase-to-today`. The projects have `deletion_policy = "PREVENT"` and the tables `deletion_protection`, so a destroy needs both turned off first.

With `billing_account`, linking billing does not clear what the sandbox wrote. The stack clears it: the datasets' defaults become null (a dataset update is a PUT, so the value goes), and the curated partitions and the four views get an explicit `0`, because the provider keeps the old value of those attributes when they are left out. The raw partitions keep `raw_retention_days`. Should the provider ever stop clearing them, the `bq` equivalents are `bq update --default_table_expiration 0` and `--default_partition_expiration 0` on a dataset, `--time_partitioning_expiration 0` on a table and `--expiration 0` on a view, followed by `expiration_time = 0` in the code, or the next update of the view writes the old date back.

The same apply creates the cost guardrails (`billing.tf`, `kill_switch.tf`):

- A monthly budget of `budget_amount` (5) on both projects, alerting at 50, 90 and 100% of the gross cost: credits are excluded, so a Free Trial alerts as if it were paid. The alerts go to `alert_email` through a Cloud Monitoring e-mail channel; the billing account's IAM recipients are off. Amounts are in the billing account's currency.
- A kill-switch on the ingestion project: the budget publishes its cost to the `billing-budget-alerts` topic several times a day, and the `billing-kill-switch` function (`data/kill-switch`) unlinks billing from that project once the month's cost reaches `kill_switch_amount` (3). Its account holds Project Billing Manager and Browser on that project only. Cost data arrives hours late, which is why the switch sits well below the budget.
- The organization's domain restriction (`iam.allowedPolicyMemberDomains`) refuses `billing-budget-alert@system.gserviceaccount.com`, which publishes to the topic, and takes no exception per principal. On the ingestion project only, the managed `iam.managed.allowedPolicyMembers` replaces it with the organization's principal set plus that one account.
- `email_test_budget = true` adds a 1-unit budget that alerts at 1%, to prove the e-mail on the first gross cent (Cloud Billing has no test send); set it back to false afterwards.

With billing the stack also puts the policy tag `pii` (`policy_tags.tf`, taxonomy `vortex-pii`) on the columns of `data/policy_tags.json` in the raw tables; only `operator_member` gets Fine-Grained Reader, and neither Owner nor Organization Administrator reads a tagged column without it. `data/scripts/sandbox.sh prove` reads each column as `pipeline-launcher` and must be refused by the tag. Change the list in that file, never in the console: the provider sends an empty tag for every column without one in the code. On the apply that creates the tag, the plan shows the raw schemas as known after apply; apply the tag alone first (`-target=google_data_catalog_policy_tag_iam_member.operator_reads_pii`) to review each column in the next plan. A plan that changes a schema also lists `INTEGER -> INT64`, two names of one type. The taxonomy and the tag have `deletion_policy = "PREVENT"`, because deleting either one takes the tag off every column; a policy or grant change may take up to 30 minutes to reach BigQuery. The Data Catalog calls also go through the `google.billing` provider.

With billing the stack also prepares the Dataflow job (`network.tf`, `dataflow.tf`, `dataflow_iam.tf`): the folder refuses default networks before the Compute API is enabled, the workers get a network without firewall rules, and the job gets a landing and a temporary bucket and the secret `vortex-hmac-key`. On the first apply, put the constraint in place alone and check it, because a default network, once created, has to be deleted by hand:

```sh
terraform apply -target=google_org_policy_policy.skip_default_network
gcloud org-policies describe compute.skipDefaultNetworkCreation --project "$(terraform output -raw ingest_project_id)" --effective
```

How to run the job: [data/README.md](../../data/README.md#running-on-dataflow). The secret's value never goes through Terraform. Add it once, from the repository root, with the file `data/scripts/generate-hmac-key.sh` wrote (the key and its version); a different key changes every pseudonym:

```sh
gcloud secrets versions add vortex-hmac-key --project="$(terraform -chdir=infra/terraform/gcp/data output -raw ingest_project_id)" --data-file=data/.env
```

On a new project the first apply stops halfway, and a second one finishes it: the managed constraint takes a few minutes to admit the budget's publisher (the topic grant fails with "do not belong to a permitted customer" until then), and the BigQuery update of `vortex_raw` may still see the project as a sandbox right after billing is linked. The Cloud Functions build reads the source from a bucket Google creates on the first deploy (`gcf-v2-sources-<number>-<region>`), so the builder's read grant is a project role whose condition names the two source buckets. The Org Policy and Budget APIs refuse user credentials without a quota project, so those resources go through the `google.billing` provider.

To test the kill-switch, publish a notification above the switch, in the account's currency, and read the project's billing:

```sh
topic=$(terraform -chdir=infra/terraform/gcp/data output -raw budget_topic)
gcloud pubsub topics publish "$topic" --message='{"costAmount": 16, "budgetAmount": 25, "currencyCode": "BRL", "costIntervalStart": "2026-11-01T00:00:00Z"}'
gcloud billing projects describe "$(terraform -chdir=infra/terraform/gcp/data output -raw ingest_project_id)"
```

Terraform does not see a cut, real or tested: the provider keeps `billing_account` in the state, so `terraform plan` reports no changes, and with billing off Cloud Storage refuses to refresh the function's source object, so a full plan fails. Linking billing back is the decision to spend again, taken by hand once you know why the switch fired:

```sh
gcloud billing projects link "$(terraform -chdir=infra/terraform/gcp/data output -raw ingest_project_id)" --billing-account=<the id in terraform.tfvars>
```

Dataset grants use `google_bigquery_dataset_access` only: `google_bigquery_dataset_iam_*` would rewrite the access list and drop the authorized dataset that lets the analytics views read `vortex_curated`. The sandbox accepts that authorized dataset between its two projects (proven on 2026-11-16: `analyst-reader` reads the view and is refused on the base tables). Should a later change break it, granting READER on `vortex_curated` to `analyst-reader` instead makes `data/scripts/sandbox.sh prove` fail on purpose.

## Checks

```sh
cd infra/terraform
terraform fmt -check -recursive
terraform -chdir=bootstrap init -backend=false -input=false
terraform -chdir=foundation init -backend=false -input=false
terraform -chdir=demo init -backend=false -input=false
terraform -chdir=site init -backend=false -input=false
terraform -chdir=gcp/data init -backend=false -input=false
terraform -chdir=bootstrap validate
terraform -chdir=foundation validate
terraform -chdir=demo validate
terraform -chdir=site validate
terraform -chdir=gcp/data validate
tflint --init --config "$PWD/.tflint.hcl"
tflint --recursive --config "$PWD/.tflint.hcl"
checkov -d . --framework terraform --quiet
```

Every `#checkov:skip` in the code states why the control does not pay for itself in this account.

## Cost

| Item | Monthly |
|---|---|
| Route 53 hosted zone | US$ 0.50 |
| State bucket (a few KB, a few requests per run) | cents |
| Budget | free (the first two budgets cost nothing) |
| Demo data volume (5 GiB gp3, always there) | US$ 0.40 |
| Demo instance (`t4g.small`), only while on | instance hours free under the T4g trial until 2026-12-31, then about US$ 0.017/hour; CPU above the baseline not earned back before stop or terminate costs US$ 0.04 per vCPU-hour (unlimited credits, not covered by the trial) |
| Demo public IPv4 address and 10 GiB root volume, only while on | US$ 0.005/hour plus about US$ 0.001/hour |
| Site distributions and their function | free within the CloudFront always free tier (1 TB out, 10 million requests and 2 million function invocations a month) |
| Site alias records (A, AAAA, HTTPS) | free: Route 53 does not charge alias queries to CloudFront |
| Site certificate (ACM) | free |
| Site buckets (a few MB) and invalidations (the first 1,000 paths a month are free) | cents |
| Google Cloud projects without billing (BigQuery sandbox) | free: 10 GB of storage and 1 TB of queries a month |
| Dataflow landing and temporary buckets, HMAC secret, network, between jobs | free within the free tiers (a few KB, one secret version); a VPC costs nothing |
| Dataflow job over the fixtures, only while it runs (about 5 minutes, one `n1-standard-1`) | paid per run; not measured yet, because billing data arrives hours late |
| Google Cloud with billing: budgets, Pub/Sub topic, policy tag, kill-switch function and its source bucket | free within the free tiers (a few notifications a day, one function instance); the paid usage (Dataflow, BigQuery above the free tier) is capped by the kill-switch |

Domain registration is paid by card: AWS credits do not cover it.
