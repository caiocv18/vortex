terraform {
  # Partial configuration: bucket, region, use_lockfile and encrypt come from
  # ../backend.hcl (git-ignored), written from the bootstrap stack's output.
  backend "s3" {
    key = "foundation/terraform.tfstate"
  }
}
