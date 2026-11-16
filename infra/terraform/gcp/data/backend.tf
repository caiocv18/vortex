terraform {
  # Same state bucket as the AWS stacks (partial configuration in ../../backend.hcl, git-ignored),
  # so `init` needs AWS credentials too: AWS_PROFILE for the state, ADC for Google Cloud.
  backend "s3" {
    key = "gcp/data/terraform.tfstate"
  }
}
