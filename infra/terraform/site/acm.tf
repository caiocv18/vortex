# One certificate for both hosts: the apex and *.<domain>, which covers login.<domain>.
# ACM public certificates cost nothing and renew themselves while the CNAME below stays.
resource "aws_acm_certificate" "site" {
  domain_name               = var.domain_name
  subject_alternative_names = ["*.${var.domain_name}"]
  validation_method         = "DNS"

  # The distributions hold the certificate, so a replacement must exist before the
  # old one goes.
  lifecycle {
    create_before_destroy = true
  }
}

# Keyed by domain name, which the plan already knows. The record name only exists once
# the certificate does, so keying by it would make the first plan fail.
resource "aws_route53_record" "certificate_validation" {
  for_each = {
    for dvo in aws_acm_certificate.site.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  # The apex and the wildcard get the same CNAME, so the second key writes over the
  # record the first one created instead of failing on it.
  allow_overwrite = true
  zone_id         = data.aws_route53_zone.primary.zone_id
  name            = each.value.name
  type            = each.value.type
  ttl             = 60
  records         = [each.value.record]
}

resource "aws_acm_certificate_validation" "site" {
  certificate_arn         = aws_acm_certificate.site.arn
  validation_record_fqdns = [for record in aws_route53_record.certificate_validation : record.fqdn]
}
