# Storage and key of the Dataflow job (ADR 0008, amendment of 2026-11-25). Who reaches what is in dataflow_iam.tf.
# Soft delete is off on both buckets: they hold personal data in clear, and a deleted object must be gone,
# not kept for seven more days.

# fixtures/ holds the events a job reads, quarantine/<run id>/ the lines it refused, personal data
# included. Both go after raw_retention_days, like the raw tables.
resource "google_storage_bucket" "landing" { # NOSONAR(terraform:S6258,terraform:S6412) same reasons as the two checkov skips below; accepted risk
  #checkov:skip=CKV_GCP_62:Data Access audit logs already record who read or wrote each object; access logs would be a second copy to keep
  #checkov:skip=CKV_GCP_78:Versioning would keep personal data past the lifecycle rule
  count = local.billing

  project                     = google_project.ingest.project_id
  name                        = "${google_project.ingest.project_id}-landing"
  location                    = var.region
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  soft_delete_policy {
    retention_duration_seconds = 0
  }

  lifecycle_rule {
    condition {
      age = var.raw_retention_days
    }
    action {
      type = "Delete"
    }
  }

  lifecycle_rule {
    condition {
      age = 1
    }
    action {
      type = "AbortIncompleteMultipartUpload"
    }
  }

  depends_on = [google_project_service.ingest_billing]
}

# Staged package and the NDJSON that FILE_LOADS hands to BigQuery, which Beam never deletes. The submit
# script empties it; the rule covers a launcher that died before that.
resource "google_storage_bucket" "dataflow_temp" { # NOSONAR(terraform:S6258,terraform:S6412) same reasons as the two checkov skips below; accepted risk
  #checkov:skip=CKV_GCP_62:Data Access audit logs already record who read or wrote each object; access logs would be a second copy to keep
  #checkov:skip=CKV_GCP_78:Versioning would keep personal data past the lifecycle rule
  count = local.billing

  project                     = google_project.ingest.project_id
  name                        = "${google_project.ingest.project_id}-dataflow-temp"
  location                    = var.region
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  soft_delete_policy {
    retention_duration_seconds = 0
  }

  lifecycle_rule {
    condition {
      age = 1
    }
    action {
      type = "Delete"
    }
  }

  lifecycle_rule {
    condition {
      age = 1
    }
    action {
      type = "AbortIncompleteMultipartUpload"
    }
  }

  depends_on = [google_project_service.ingest_billing]
}

# The key's value never passes through Terraform, or it would sit in the state: the operator adds the
# version from data/.env (infra/terraform/README.md).
resource "google_secret_manager_secret" "hmac_key" {
  #checkov:skip=CKV_GCP_82:A customer managed key needs Cloud KMS, billed every month; the secret is encrypted at rest with Google-managed keys
  count = local.billing

  project             = google_project.ingest.project_id
  secret_id           = "vortex-hmac-key"
  deletion_protection = true

  replication {
    user_managed {
      replicas {
        location = var.region
      }
    }
  }

  depends_on = [google_project_service.dataflow]
}
