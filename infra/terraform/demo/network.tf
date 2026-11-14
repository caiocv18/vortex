data "aws_ec2_instance_type_offerings" "this" {
  location_type = "availability-zone-id"

  filter {
    name   = "instance-type"
    values = [var.instance_type]
  }
}

# Managed by AWS: the addresses CloudFront uses to reach origins. Each list weighs 55
# rules against the default quota of 60 per security group, and AWS counts a rule that
# references a prefix list in both the IPv4 and the IPv6 quota, so each list gets a
# security group of its own.
data "aws_ec2_managed_prefix_list" "cloudfront_ipv4" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

data "aws_ec2_managed_prefix_list" "cloudfront_ipv6" {
  name = "com.amazonaws.global.ipv6.cloudfront.origin-facing"
}

resource "aws_vpc" "demo" {
  #checkov:skip=CKV2_AWS_11:Flow logs to CloudWatch cost money and nobody would read them; the only inbound path is CloudFront on 443
  cidr_block                       = "10.42.0.0/16"
  assign_generated_ipv6_cidr_block = true
  enable_dns_support               = true
  enable_dns_hostnames             = true

  tags = {
    Name = "vortex-demo"
  }
}

# The default group of a new VPC allows all traffic between its members. Adopting it
# with no rules leaves it empty, so nothing that falls back to it gets any access.
resource "aws_default_security_group" "demo" {
  vpc_id = aws_vpc.demo.id

  tags = {
    Name = "vortex-demo-default-unused"
  }
}

resource "aws_subnet" "public" {
  vpc_id                          = aws_vpc.demo.id
  availability_zone_id            = var.availability_zone_id
  cidr_block                      = cidrsubnet(aws_vpc.demo.cidr_block, 8, 0)
  ipv6_cidr_block                 = cidrsubnet(aws_vpc.demo.ipv6_cidr_block, 8, 0)
  assign_ipv6_address_on_creation = true

  tags = {
    Name = "vortex-demo-public"
  }

  lifecycle {
    # The offerings come back in no particular order, so the list is searched,
    # never indexed.
    precondition {
      condition     = contains(data.aws_ec2_instance_type_offerings.this.locations, var.availability_zone_id)
      error_message = "The availability zone id does not offer the instance type; pick another zone id."
    }
  }
}

resource "aws_internet_gateway" "demo" {
  vpc_id = aws_vpc.demo.id

  tags = {
    Name = "vortex-demo"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.demo.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.demo.id
  }

  route {
    ipv6_cidr_block = "::/0"
    gateway_id      = aws_internet_gateway.demo.id
  }

  tags = {
    Name = "vortex-demo-public"
  }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "origin" {
  name        = "demo-origin"
  description = "HTTPS in from CloudFront origin-facing servers (IPv4) only, HTTPS out only"
  vpc_id      = aws_vpc.demo.id

  tags = {
    Name = "demo-origin"
  }
}

# Ingress only. Terraform removes the allow-all egress rule AWS adds to a new group,
# so all outbound traffic is ruled by demo-origin.
resource "aws_security_group" "origin_ipv6" {
  name        = "demo-origin-ipv6"
  description = "HTTPS in from CloudFront origin-facing servers (IPv6) only"
  vpc_id      = aws_vpc.demo.id

  tags = {
    Name = "demo-origin-ipv6"
  }
}

resource "aws_vpc_security_group_ingress_rule" "https_from_cloudfront_ipv4" {
  security_group_id = aws_security_group.origin.id
  description       = "HTTPS from CloudFront origin-facing servers (IPv4)"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront_ipv4.id
}

resource "aws_vpc_security_group_ingress_rule" "https_from_cloudfront_ipv6" {
  security_group_id = aws_security_group.origin_ipv6.id
  description       = "HTTPS from CloudFront origin-facing servers (IPv6)"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront_ipv6.id
}

# Package repositories, GitHub, GHCR and the Systems Manager endpoints are all HTTPS.
# DNS, NTP and the instance metadata service use link-local addresses that security
# groups do not filter.
resource "aws_vpc_security_group_egress_rule" "https_out_ipv4" {
  security_group_id = aws_security_group.origin.id
  description       = "HTTPS out (IPv4)"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "https_out_ipv6" {
  security_group_id = aws_security_group.origin.id
  description       = "HTTPS out (IPv6)"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv6         = "::/0"
}
