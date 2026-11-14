resource "aws_cloudfront_origin_access_control" "site" {
  name                              = "vortex-site-${var.name}"
  description                       = "Signed requests from the ${var.domain_name} distribution to its bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

locals {
  origin_id = "s3-${var.name}"
}

resource "aws_cloudfront_distribution" "site" { # NOSONAR(terraform:S6258) standard logs need a log bucket and nobody would read them; accepted risk
  #checkov:skip=CKV_AWS_68:AWS WAF costs at least US$ 5/month per web ACL, half the budget, for a static site with no backend yet
  #checkov:skip=CKV2_AWS_47:The Log4j rule needs AWS WAF, and nothing behind this distribution runs Java
  #checkov:skip=CKV_AWS_86:Standard logs need a log bucket and nobody would read them
  #checkov:skip=CKV_AWS_310:One S3 origin in one region; a failover origin would need a second bucket kept in sync
  #checkov:skip=CKV_AWS_374:The portfolio is public on purpose; no country is blocked
  enabled             = true
  comment             = "Vortex ${var.name} single-page app at ${var.domain_name} (infra/terraform/site)"
  aliases             = [var.domain_name]
  default_root_object = "index.html"
  is_ipv6_enabled     = true
  http_version        = "http2and3"
  # Every edge, the South America ones included: within the always free tier the price
  # class changes nothing, and the audience is in Brazil.
  price_class = "PriceClass_All"

  # No s3_origin_config: with an origin access control, CloudFront signs the requests
  # itself and the legacy origin access identity stays empty.
  origin {
    origin_id                = local.origin_id
    domain_name              = aws_s3_bucket.site.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.site.id
  }

  default_cache_behavior {
    target_origin_id           = local.origin_id
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = var.cache_policy_id
    response_headers_policy_id = var.response_headers_policy_id

    function_association {
      event_type   = "viewer-request"
      function_arn = var.function_arn
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = var.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}
