resource "aws_route53_zone" "primary" {
  #checkov:skip=CKV2_AWS_38:DNSSEC signing needs a customer managed KMS key (US$ 1/month) against a US$ 10/month budget
  #checkov:skip=CKV2_AWS_39:Query logging to CloudWatch costs money and nobody would read it
  name    = var.domain_name
  comment = "Public zone of the portfolio, managed by Terraform in caiocv18/vortex"

  # A new zone gets new name servers, which would break the delegation at the registrar.
  lifecycle {
    prevent_destroy = true
  }
}

# Route 53 keeps one TXT record set per name, so every apex TXT value lives here.
# The first is the Cloud Identity domain verification of the Google Cloud organization.
resource "aws_route53_record" "apex_txt" {
  count = length(var.apex_txt_records) > 0 ? 1 : 0

  zone_id = aws_route53_zone.primary.zone_id
  name    = var.domain_name
  type    = "TXT"
  ttl     = 300
  records = var.apex_txt_records
}
