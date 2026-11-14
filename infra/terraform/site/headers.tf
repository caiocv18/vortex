locals {
  # Report-Only until the API is online behind these hosts: a violation is reported in
  # the browser console and nothing is blocked. It turns into an enforced
  # Content-Security-Policy with the item that puts the API online.
  # 'unsafe-inline' in style-src covers the <style> elements Vuetify and react-hot-toast
  # inject at runtime; the Google Fonts hosts cover the login app's font stylesheet.
  content_security_policy = join("; ", [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
    "font-src 'self' https://fonts.gstatic.com data:",
    "img-src 'self' data:",
    "connect-src 'self'",
    "frame-ancestors 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "object-src 'none'",
  ])
}

# One policy for both distributions. CloudFront adds these headers to every response,
# from the cache or from the origin, and overrides any the origin sends.
resource "aws_cloudfront_response_headers_policy" "site" {
  name    = "vortex-site-security-headers"
  comment = "Security headers of the static single-page apps (ADR 0007)"

  security_headers_config {
    # The whole .dev TLD is on the browsers' HSTS preload list, so every host under it
    # is HTTPS-only already; preload and includeSubDomains add no new commitment.
    strict_transport_security {
      access_control_max_age_sec = 31536000
      include_subdomains         = true
      preload                    = true
      override                   = true
    }

    content_type_options {
      override = true
    }

    frame_options {
      frame_option = "DENY"
      override     = true
    }

    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }
  }

  custom_headers_config {
    items {
      header   = "Permissions-Policy"
      value    = "camera=(), microphone=(), geolocation=()"
      override = true
    }

    items {
      header   = "Content-Security-Policy-Report-Only"
      value    = local.content_security_policy
      override = true
    }
  }

  # A policy in use can't be deleted. If it is ever removed, this makes Terraform update
  # the distributions to drop it before deleting it. A rename is an in-place update.
  lifecycle {
    create_before_destroy = true
  }
}
