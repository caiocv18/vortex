# Shared by both distributions on the viewer-request event. The code and its cases
# live in spa-rewrite.js.
resource "aws_cloudfront_function" "spa_rewrite" {
  name    = "vortex-spa-rewrite"
  runtime = "cloudfront-js-2.0"
  comment = "Rewrites deep links of the single-page apps to /index.html (ADR 0007)"
  publish = true
  code    = file("${path.module}/spa-rewrite.js")

  # A function in use can't be deleted: on a rename, the distributions must move to
  # the new one before the old one goes.
  lifecycle {
    create_before_destroy = true
  }
}
