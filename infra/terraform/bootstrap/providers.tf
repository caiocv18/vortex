# Credentials come from the environment (AWS_PROFILE locally, OIDC in CI later),
# never from this code.
provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project    = "vortex"
      Stack      = "bootstrap"
      ManagedBy  = "terraform"
      Repository = "caiocv18/vortex"
    }
  }
}
