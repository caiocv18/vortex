data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

# The public zone belongs to the foundation stack; this stack only adds records to it.
data "aws_route53_zone" "primary" {
  name         = var.domain_name
  private_zone = false
}

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

locals {
  login_domain = "${var.login_subdomain}.${var.domain_name}"

  # One single-page app per host: the inventory app at the apex, the login app on its
  # subdomain. Each calls the API on its own origin, so neither needs CORS (ADR 0007).
  sites = {
    app   = var.domain_name
    login = local.login_domain
  }
}

module "spa" {
  source   = "./modules/spa"
  for_each = local.sites

  name        = each.key
  domain_name = each.value
  # Account regional namespace (the -an suffix), like the state bucket: only this
  # account can ever create these names.
  bucket_name = "vortex-site-${each.key}-${data.aws_caller_identity.current.account_id}-${data.aws_region.current.region}-an"
  zone_id     = data.aws_route53_zone.primary.zone_id

  # The validation's ARN, not the certificate's: the distributions wait until it is issued.
  certificate_arn            = aws_acm_certificate_validation.site.certificate_arn
  cache_policy_id            = data.aws_cloudfront_cache_policy.caching_optimized.id
  response_headers_policy_id = aws_cloudfront_response_headers_policy.site.id
  function_arn               = aws_cloudfront_function.spa_rewrite.arn
}
