output "bucket" {
  description = "Name of the bucket that holds the built site."
  value       = aws_s3_bucket.site.id
}

output "distribution_id" {
  description = "Id of the CloudFront distribution, for invalidations."
  value       = aws_cloudfront_distribution.site.id
}

output "url" {
  description = "Public URL of the site."
  value       = "https://${var.domain_name}"
}
