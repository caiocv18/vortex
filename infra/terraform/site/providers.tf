# Credentials come from the environment (AWS_PROFILE locally, OIDC in CI later),
# never from this code.
provider "aws" {
  # CloudFront only reads certificates from ACM in us-east-1, so the whole stack
  # lives there instead of taking the region from a variable.
  region = "us-east-1"

  default_tags {
    tags = {
      Project    = "vortex"
      Stack      = "site"
      ManagedBy  = "terraform"
      Repository = "caiocv18/vortex"
    }
  }
}
