locals {
  # Without a billing account both projects live in the BigQuery sandbox, which sets a 60-day
  # expiration on every dataset, table and partition. Declaring it keeps the plan free of drift.
  sandbox               = var.billing_account == null
  day_ms                = 86400000
  sandbox_expiration_ms = local.sandbox ? 60 * local.day_ms : null
  raw_retention_ms      = var.raw_retention_days * local.day_ms

  # APIs that cost nothing to enable; the paid ones arrive with billing
  services = ["bigquery.googleapis.com", "iam.googleapis.com", "iamcredentials.googleapis.com"]
}

resource "random_id" "suffix" {
  byte_length = 3
}

resource "google_folder" "vortex" {
  display_name        = "vortex"
  parent              = "organizations/${var.org_id}"
  deletion_protection = true
}

# Two projects, so the analytics side never holds the base tables: an analyst
# reads views that are authorized on the curated dataset and pays for the queries
# in the analytics project.
resource "google_project" "ingest" {
  #checkov:skip=CKV_GCP_27:Turning auto_create_network off makes Terraform enable the Compute API to delete the default network, which a project without billing can't do; the Dataflow block brings a network of its own
  name                = "Vortex ingest"
  project_id          = "${var.project_prefix}-ingest-${random_id.suffix.hex}"
  folder_id           = google_folder.vortex.folder_id
  billing_account     = var.billing_account
  auto_create_network = true
  deletion_policy     = "PREVENT"
}

resource "google_project" "analytics" {
  #checkov:skip=CKV_GCP_27:Same as the ingestion project, and this one never runs compute
  name                = "Vortex analytics"
  project_id          = "${var.project_prefix}-analytics-${random_id.suffix.hex}"
  folder_id           = google_folder.vortex.folder_id
  billing_account     = var.billing_account
  auto_create_network = true
  deletion_policy     = "PREVENT"
}

# Data Access audit logs for every service: who read or wrote what, and who acted as
# which service account. BigQuery logs data access on its own; this adds the rest,
# impersonation included. A few MB a month, inside the Cloud Logging free tier.
resource "google_project_iam_audit_config" "ingest" {
  project = google_project.ingest.project_id
  service = "allServices"

  dynamic "audit_log_config" {
    for_each = ["ADMIN_READ", "DATA_READ", "DATA_WRITE"]
    content {
      log_type = audit_log_config.value
    }
  }
}

resource "google_project_iam_audit_config" "analytics" {
  project = google_project.analytics.project_id
  service = "allServices"

  dynamic "audit_log_config" {
    for_each = ["ADMIN_READ", "DATA_READ", "DATA_WRITE"]
    content {
      log_type = audit_log_config.value
    }
  }
}

resource "google_project_service" "ingest" {
  for_each = toset(local.services)

  project            = google_project.ingest.project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_project_service" "analytics" {
  for_each = toset(local.services)

  project            = google_project.analytics.project_id
  service            = each.value
  disable_on_destroy = false
}
