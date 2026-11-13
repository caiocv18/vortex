# Terraform

Infrastructure as code for the AWS account that hosts the Vortex portfolio. The account runs on the AWS Free plan, and the whole project has a ceiling of **US$ 10/month** in gross cost.

| Stack | State key | What it owns |
|---|---|---|
| `bootstrap/` | `bootstrap/terraform.tfstate` | The S3 bucket that stores every stack's state, its own included |
| `foundation/` | `foundation/terraform.tfstate` | The public Route 53 zone of the portfolio domain and the gross monthly cost budget |

Later stacks (static site on S3 + CloudFront, the on-demand demo on EC2) get their own folder and state key.

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
- A profile that can manage S3, Route 53 and Budgets: run `aws login --profile <profile>`, then `export AWS_PROFILE=<profile>`.

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

## Checks

```sh
cd infra/terraform
terraform fmt -check -recursive
terraform -chdir=bootstrap init -backend=false -input=false
terraform -chdir=foundation init -backend=false -input=false
terraform -chdir=bootstrap validate
terraform -chdir=foundation validate
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

Domain registration is paid by card: AWS credits do not cover it.
