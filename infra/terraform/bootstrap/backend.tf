terraform {
  # Partial configuration: bucket, region, use_lockfile and encrypt come from
  # ../backend.hcl (git-ignored), so the account id never enters the repository.
  # The very first apply runs with a local backend override; see ../README.md.
  backend "s3" {
    key = "bootstrap/terraform.tfstate"
  }
}
