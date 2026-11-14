# 6. An on-demand demo instance, reached only through Session Manager

- **Status:** Accepted
- **Date:** 2026-11-14
- **Pull request:** #22

## Context

The public demo will run both backends, PostgreSQL and a reverse proxy with Docker Compose on one arm64 instance, behind CloudFront. The AWS account runs on the Free plan, with a ceiling of US$ 10/month in gross cost, so the instance must exist only while the demo is shown. Its data must not go away with it.

Facts that shaped the design, as of 2026-11-14:

- `github.com`, `ghcr.io`, `release-assets.githubusercontent.com` and the default Systems Manager endpoints have no IPv6 address, so the instance needs outbound IPv4.
- A NAT gateway costs about US$ 33/month, and the three interface endpoints Session Manager needs in a private subnet cost about US$ 22/month. A public IPv4 address costs US$ 0.005/hour, only while the instance runs.
- The CloudFront origin-facing prefix lists (IPv4 and IPv6) weigh 55 rules each against the default quota of 60 rules per security group, and AWS counts a rule that references a prefix list in both the IPv4 and the IPv6 quota, so the two lists cannot share one group. Either list admits every CloudFront distribution, of any account.
- The free trial of T4g instances runs until 2026-12-31. It covers instance hours, not surplus CPU credits.

## Decision

- **Stack:** `infra/terraform/demo/`, state key `demo/terraform.tfstate`. The variable `demo_enabled` (default `false`) creates or terminates the instance and its volume attachment; the network, the role and the data volume stay either way.
- **Instance:** `t4g.small` (2 vCPU, 2 GiB, Graviton), the only type the variable accepts. Amazon Linux 2023 arm64 from AWS's public SSM parameter, read through `insecure_value` so the AMI id shows in the plan. No key pair, encrypted 10 GiB gp3 root volume, unlimited CPU credits, and a shutdown from inside stops instead of terminating. `ignore_changes = [ami, user_data]`: a new AMI or a new user data applies to the next launch, never to a running demo.
- **Access:** Session Manager only. The instance role has `AmazonSSMManagedInstanceCore` plus an inline policy that denies every `ssm:GetParameter*` action everywhere except `/vortex/prd` and `/turnward/prd`, and allows `ssm:GetParametersByPath` on those two paths. The managed policy grants `ssm:GetParameter` and `ssm:GetParameters` on every parameter, and nothing by path, and it cannot be narrowed. The user data disables `sshd`, and no security group rule opens port 22.
- **Metadata:** IMDSv2 only, with a hop limit of 1, so a container on a bridge network cannot reach the role's credentials.
- **Network:** a VPC of its own, with an Amazon-provided IPv6 block, one public dual-stack subnet in a fixed availability zone id, an internet gateway and nothing else: no NAT gateway, no load balancer, no endpoints. The VPC's default security group is adopted with no rules.
- **Security groups `demo-origin` and `demo-origin-ipv6`:** inbound 443 only, from the IPv4 CloudFront origin-facing prefix list in the first and from the IPv6 one in the second; outbound 443 only, in the first. Each list takes 55 of its group's 60 rules, so another port would need two more groups or a quota increase. One port keeps the setup at two groups: the origin terminates TLS itself, and port 80 never opens.
- **Data:** a separate encrypted 5 GiB gp3 volume, outside the `count` and guarded by `prevent_destroy`, mounted at `/var/lib/docker/volumes`. It is detached with `stop_instance_before_detaching`. It can only grow, and the next launch grows the filesystem to match.
- **User data:** `infra/cloud/user-data.sh`, plain bash, with a short header from Terraform that sets the volume serial and the Compose version and checksum. It is not a `templatefile`, which would read every `${` and `%{` in the script. In order, it:
  1. stops and disables `sshd`, before any step that can abort, because user data never runs twice;
  2. installs `docker` and `git`;
  3. waits up to 5 minutes for the data volume, which Terraform attaches after the instance starts;
  4. formats the volume only when `blkid -p` exits 2 (no signature found, or the device could not be probed) and `mkfs.xfs`, run without `-f`, finds no signature in its own probe. `mkfs.xfs` also refuses when that probe fails, so it is the lock that holds in that case. Any signature other than XFS aborts the script;
  5. mounts it by UUID with `nofail`, grows the filesystem when the volume grew, and makes `docker.service` require the mount, so containers never write their volumes to the root disk;
  6. adds 2 GiB of swap;
  7. installs Docker Compose 5.7.1 as a CLI plugin, checked against its SHA-256;
  8. starts Docker;
  9. clones `infra/cloud` of the default branch into `/opt/vortex`, shallow and sparse.

## Alternatives considered

- **SSH with a key pair and port 22 open to one address.** A key to keep and rotate, an address that changes, and an open port. Session Manager needs no inbound port and no key.
- **A private subnet with a NAT gateway, or with SSM interface endpoints.** About US$ 33/month or US$ 22/month, over the whole budget.
- **IPv6 only.** GitHub and GHCR cannot be reached over IPv6, and Systems Manager would need its dual-stack `api.aws` endpoints and an agent configured to use them instead of its defaults.
- **One security group for both prefix lists.** It would need a quota of at least 110 rules per group, which is a request to AWS rather than a default.
- **A load balancer in front of the instance.** About US$ 16/month, and CloudFront already terminates the public TLS.
- **Docker volumes on the root disk.** They would go away with every termination.
- **Another architecture.** If `t4g.small` stops being available, or once its trial ends and the price matters, the plan B is a `t3.small` (amd64). These change together: the variable's validation, the image parameter in `ec2.tf` (`al2023-ami-kernel-default-x86_64`), the architecture check and the Compose binary name in the user data (`docker-compose-linux-x86_64`), and the Compose checksum. The images are already multi-arch (ADR 0004).

## Consequences

- The prefix list admits every CloudFront distribution, not only ours. When the API goes live, the origin must authenticate the distribution, with a secret header kept as a SecureString under `/vortex/prd/` or with mutual TLS.
- The public IPv4 address changes on every launch, so the API's CloudFront origin will need a DNS name that follows the instance.
- Cost while the instance runs: the instance hours (free under the T4g trial until 2026-12-31, about US$ 0.017/hour after it), US$ 0.005/hour of public IPv4 and the 10 GiB root volume. With unlimited CPU credits, CPU above the baseline that is not earned back before the instance stops or terminates costs US$ 0.04 per vCPU-hour, and the trial does not cover it. A T4g gets no launch credits, so the burst at boot can show up on a short demo. The 5 GiB data volume costs about US$ 0.40/month even while the demo is off.
- With a hop limit of 1, a container that needs AWS credentials has to get them some other way than the instance role.
- The user data log, `/var/log/vortex-user-data.log`, lives on the root volume and goes away with the instance.
- The data volume takes its zone from `availability_zone_id`, not from the subnet, so a subnet replaced in the same zone id leaves the volume alone. Changing the zone id replaces both, and the volume cannot be replaced without first removing `prevent_destroy`.
- The clone takes the default branch, so an instance launched before this change reached `master` has no `infra/cloud` in `/opt/vortex`; its log says so, and the next launch fixes it.

## References

- `infra/terraform/demo/`: the stack.
- `infra/cloud/user-data.sh`: the first boot of every instance.
- `infra/terraform/README.md`, section "Demo": how to turn the demo on and off.
