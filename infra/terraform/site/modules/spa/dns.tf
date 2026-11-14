# Route 53 answers alias queries to CloudFront at no charge, but only for a record
# type that exists: browsers that also ask for the HTTPS type would otherwise get a
# billed empty answer. The HTTPS record also tells them HTTP/3 is available before
# the first connection.
resource "aws_route53_record" "site" {
  for_each = toset(["A", "AAAA", "HTTPS"])

  zone_id = var.zone_id
  name    = var.domain_name
  type    = each.key

  alias {
    name                   = aws_cloudfront_distribution.site.domain_name
    zone_id                = aws_cloudfront_distribution.site.hosted_zone_id
    evaluate_target_health = false
  }
}
