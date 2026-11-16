# Credentials are Application Default Credentials of the operator
# (gcloud auth application-default login), never a service account key: the
# organization forbids creating them.
provider "google" {
  region = var.region

  default_labels = {
    project    = "vortex"
    stack      = "gcp-data"
    managed-by = "terraform"
  }
}
