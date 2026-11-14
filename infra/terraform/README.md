# Terraform

Infrastructure as code for the AWS account that hosts the Vortex portfolio. The account runs on the AWS Free plan, and the whole project has a ceiling of **US$ 10/month** in gross cost.

| Stack | State key | What it owns |
|---|---|---|
| `bootstrap/` | `bootstrap/terraform.tfstate` | The S3 bucket that stores every stack's state, its own included |
| `foundation/` | `foundation/terraform.tfstate` | The public Route 53 zone of the portfolio domain and the gross monthly cost budget |
| `demo/` | `demo/terraform.tfstate` | The on-demand demo instance, reached only through Session Manager, with its network, role and data volume ([ADR 0006](../../docs/adr/0006-on-demand-demo-instance-behind-session-manager.md)) |
| `site/` | `site/terraform.tfstate` | The two single-page apps on private S3 buckets behind CloudFront, with their certificate, headers policy, routing function and DNS records ([ADR 0007](../../docs/adr/0007-static-spas-on-cloudfront.md)) |

A later stack gets its own folder and state key.

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

## Checks

```sh
cd infra/terraform
terraform fmt -check -recursive
terraform -chdir=bootstrap init -backend=false -input=false
terraform -chdir=foundation init -backend=false -input=false
terraform -chdir=demo init -backend=false -input=false
terraform -chdir=site init -backend=false -input=false
terraform -chdir=bootstrap validate
terraform -chdir=foundation validate
terraform -chdir=demo validate
terraform -chdir=site validate
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

Domain registration is paid by card: AWS credits do not cover it.
