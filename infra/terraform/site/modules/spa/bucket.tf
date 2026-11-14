resource "aws_s3_bucket" "site" { # NOSONAR(terraform:S6258) access logging needs a second bucket for files rebuilt from git on every publish; accepted risk
  #checkov:skip=CKV_AWS_18:Access logging needs a second bucket; CloudFront is the only reader of this one
  #checkov:skip=CKV_AWS_21:Versioning adds nothing: every object is rebuilt from a git commit by scripts/publicar-fronts.sh
  #checkov:skip=CKV2_AWS_61:Without versioning there are no old versions for a lifecycle rule to expire
  #checkov:skip=CKV_AWS_144:Cross-region replication doubles the cost of files rebuilt from git
  #checkov:skip=CKV_AWS_145:SSE-S3 by decision; a customer managed KMS key costs US$ 1/month and CloudFront would need a grant on it
  #checkov:skip=CKV2_AWS_62:Nothing consumes events from a site bucket
  bucket           = var.bucket_name
  bucket_namespace = "account-regional"
}

resource "aws_s3_bucket_ownership_controls" "site" {
  bucket = aws_s3_bucket.site.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "site" {
  bucket = aws_s3_bucket.site.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "site" {
  bucket = aws_s3_bucket.site.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Only this distribution reads the bucket, through its origin access control. ListBucket
# makes S3 answer 404 instead of 403 for a missing object, so a broken asset link shows
# up as what it is.
data "aws_iam_policy_document" "site" {
  statement {
    sid       = "AllowCloudFrontRead"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.site.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.site.arn]
    }
  }

  statement {
    sid       = "AllowCloudFrontList"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.site.arn]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.site.arn]
    }
  }

  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.site.arn,
      "${aws_s3_bucket.site.arn}/*",
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

resource "aws_s3_bucket_policy" "site" {
  bucket = aws_s3_bucket.site.id
  policy = data.aws_iam_policy_document.site.json

  # S3 rejects a bucket policy while the public access block is still being applied.
  depends_on = [aws_s3_bucket_public_access_block.site]
}
