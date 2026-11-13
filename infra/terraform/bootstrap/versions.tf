terraform {
  # 1.15 is the first release whose S3 backend reads `aws login` sessions;
  # 1.11 made the native S3 lock file (use_lockfile) stable.
  required_version = ">= 1.17.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.73"
    }
  }
}
