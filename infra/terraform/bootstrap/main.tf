data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

locals {
  # Account regional namespace (the -an suffix): only this account can ever create
  # this name, so it can't be squatted before the first apply or re-created by
  # someone else if the bucket is ever deleted outside Terraform.
  state_bucket = "vortex-tfstate-${data.aws_caller_identity.current.account_id}-${data.aws_region.current.region}-an"
}

resource "aws_s3_bucket" "state" { # NOSONAR(terraform:S6258) access logging needs a second bucket that would itself be unlogged; single-owner bucket, accepted risk
  #checkov:skip=CKV_AWS_144:Cross-region replication doubles storage and request cost; versioning already covers a bad apply
  #checkov:skip=CKV_AWS_145:SSE-S3 by decision; a customer managed KMS key costs US$ 1/month against a US$ 10/month budget
  #checkov:skip=CKV_AWS_18:Access logging needs a second bucket and only the owner ever touches this one
  #checkov:skip=CKV2_AWS_62:Nothing consumes events from the state bucket
  bucket           = local.state_bucket
  bucket_namespace = "account-regional"

  # Destroying this bucket would destroy every stack's state, including its own.
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_ownership_controls" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  # Versioning must be on before noncurrent-version rules mean anything.
  depends_on = [aws_s3_bucket_versioning.state]

  rule {
    id     = "expire-old-state-versions"
    status = "Enabled"

    filter {}

    # Every run writes and deletes a .tflock object, so old versions and
    # delete markers pile up; keep the 10 newest regardless of age.
    noncurrent_version_expiration {
      noncurrent_days           = var.noncurrent_version_days
      newer_noncurrent_versions = 10
    }

    expiration {
      expired_object_delete_marker = true
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

data "aws_iam_policy_document" "state" {
  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.state.arn,
      "${aws_s3_bucket.state.arn}/*",
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id
  policy = data.aws_iam_policy_document.state.json

  # S3 rejects a bucket policy while the public access block is still being applied.
  depends_on = [aws_s3_bucket_public_access_block.state]
}
