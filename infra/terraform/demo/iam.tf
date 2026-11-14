data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_partition" "current" {}

locals {
  parameter_arn_prefix = "arn:${data.aws_partition.current.partition}:ssm:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:parameter"

  # The only parameter paths the instance may read: the production secrets of the two
  # projects that will share it. GetParametersByPath is authorized on the path itself
  # and GetParameter on each name under it, so each path appears with and without the
  # trailing wildcard.
  readable_parameter_arns = flatten([
    for path in ["vortex/prd", "turnward/prd"] : [
      "${local.parameter_arn_prefix}/${path}",
      "${local.parameter_arn_prefix}/${path}/*",
    ]
  ])
}

data "aws_iam_policy_document" "assume_ec2" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "demo" {
  name               = "vortex-demo-instance"
  description        = "Session Manager access for the demo instance, and reads of its own parameters"
  assume_role_policy = data.aws_iam_policy_document.assume_ec2.json
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.demo.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# AmazonSSMManagedInstanceCore grants ssm:GetParameter and ssm:GetParameters on every
# parameter, and nothing by path. A managed policy can't be narrowed, so an explicit
# Deny cuts every read action down to the two paths above (so no later Allow can reach
# past them), and an Allow adds GetParametersByPath on those paths only.
# The parameters will be SecureStrings under the aws/ssm key, whose key policy already
# lets any principal of the account decrypt through SSM, so no KMS statement is needed.
data "aws_iam_policy_document" "parameter_scope" {
  statement {
    sid           = "DenyParametersOutsideTheProjects"
    effect        = "Deny"
    actions       = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:GetParameterHistory"]
    not_resources = local.readable_parameter_arns
  }

  statement {
    sid       = "ReadTheProjectPathsByPath"
    effect    = "Allow"
    actions   = ["ssm:GetParametersByPath"]
    resources = local.readable_parameter_arns
  }
}

resource "aws_iam_role_policy" "parameter_scope" {
  name   = "parameter-scope"
  role   = aws_iam_role.demo.id
  policy = data.aws_iam_policy_document.parameter_scope.json
}

resource "aws_iam_instance_profile" "demo" {
  name = "vortex-demo-instance"
  role = aws_iam_role.demo.name
}
