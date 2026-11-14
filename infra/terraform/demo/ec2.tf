# insecure_value, because value is sensitive and would hide the AMI id in every plan.
# The parameter holds a public AMI id, not a secret.
data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

locals {
  # NVMe exposes the volume id, without its hyphen, as the device serial:
  # /dev/disk/by-id/nvme-Amazon_Elastic_Block_Store_vol0123...
  data_volume_serial = replace(aws_ebs_volume.data.id, "-", "")

  # The script is plain bash and goes in as is (no templatefile, which would read
  # every ${ and %{ in it); only this header carries values from Terraform.
  user_data = join("", [
    <<-EOT
    #!/bin/bash
    readonly DATA_VOLUME_SERIAL='${local.data_volume_serial}'
    readonly COMPOSE_VERSION='${var.compose_version}'
    readonly COMPOSE_SHA256='${var.compose_sha256}'
    EOT
    ,
    file("${path.module}/../../cloud/user-data.sh"),
  ])
}

resource "aws_instance" "demo" {
  #checkov:skip=CKV_AWS_88:The origin needs a public IPv4 address: github.com, ghcr.io and the default Systems Manager endpoints have no IPv6, and a NAT gateway costs about US$ 33/month
  #checkov:skip=CKV_AWS_126:Detailed monitoring costs money; the free 5-minute metrics are enough for an on-demand demo
  count = var.demo_enabled ? 1 : 0

  ami                                  = data.aws_ssm_parameter.al2023_arm64.insecure_value
  instance_type                        = var.instance_type
  subnet_id                            = aws_subnet.public.id
  vpc_security_group_ids               = [aws_security_group.origin.id, aws_security_group.origin_ipv6.id]
  iam_instance_profile                 = aws_iam_instance_profile.demo.name
  associate_public_ip_address          = true # NOSONAR(terraform:S6329) outbound IPv4 is required (no IPv6 on GitHub, GHCR or the default SSM endpoints); inbound is 443 from CloudFront only
  ebs_optimized                        = true
  monitoring                           = false
  instance_initiated_shutdown_behavior = "stop"
  user_data                            = local.user_data

  credit_specification {
    cpu_credits = "unlimited"
  }

  # IMDSv2 only, and a hop limit of 1 keeps the role's credentials out of reach of
  # containers on a bridge network, which sit one hop away.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "disabled"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size_gb
    encrypted             = true
    delete_on_termination = true
  }

  tags = {
    Name = "vortex-demo"
  }

  lifecycle {
    # A new AMI or a change to the user data must not replace a running demo.
    # Both take effect on the next launch (demo_enabled false, then true).
    ignore_changes = [ami, user_data]
  }
}

# The volume's zone comes from the zone id, known at plan time, and not from the subnet:
# a subnet replaced in the same zone id must never replace the volume.
data "aws_availability_zone" "demo" {
  zone_id = var.availability_zone_id
}

# Outside the count: the data outlives every instance.
resource "aws_ebs_volume" "data" {
  #checkov:skip=CKV_AWS_189:A customer managed KMS key costs US$ 1/month; the AWS managed key encrypts the volume
  availability_zone = data.aws_availability_zone.demo.name
  type              = "gp3"
  size              = var.data_volume_size_gb
  encrypted         = true

  tags = {
    Name = "vortex-demo-data"
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_volume_attachment" "data" {
  count = var.demo_enabled ? 1 : 0

  device_name = "/dev/sdf"
  volume_id   = aws_ebs_volume.data.id
  instance_id = aws_instance.demo[0].id

  # Detaching a mounted volume from a running instance can corrupt it; stopping the
  # instance first lets the filesystem unmount cleanly.
  stop_instance_before_detaching = true
}
