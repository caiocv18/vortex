# Grants of the Dataflow job (ADR 0008, amendment of 2026-11-25). The launcher submits and may act only as
# the worker; the worker reads the landing, writes the tables and reads the key. Neither one holds
# dataflow.admin, and no one but the operator reads a tagged column.

# dataflow.worker also carries storage.objects.get and create on every bucket of the project, but neither
# list nor delete, so the bucket grants below are still needed. bigquery.jobUser is what lets FILE_LOADS
# start its load jobs.
resource "google_project_iam_member" "worker" {
  for_each = local.sandbox ? toset([]) : toset(["roles/dataflow.worker", "roles/bigquery.jobUser"])

  project = google_project.ingest.project_id
  role    = each.value
  member  = google_service_account.dataflow_worker.member
}

resource "google_bigquery_dataset_access" "worker_writes" {
  for_each = local.sandbox ? {} : {
    raw     = google_bigquery_dataset.raw.dataset_id
    curated = google_bigquery_dataset.curated.dataset_id
  }

  project       = google_project.ingest.project_id
  dataset_id    = each.value
  role          = "WRITER"
  user_by_email = google_service_account.dataflow_worker.email
}

resource "google_secret_manager_secret_iam_member" "worker_reads_key" {
  count = local.billing

  project   = google_project.ingest.project_id
  secret_id = google_secret_manager_secret.hmac_key[0].secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = google_service_account.dataflow_worker.member
}

resource "google_storage_bucket_iam_member" "worker_temp" {
  count = local.billing

  bucket = google_storage_bucket.dataflow_temp[0].name
  role   = "roles/storage.objectUser"
  member = google_service_account.dataflow_worker.member
}

resource "google_storage_bucket_iam_member" "worker_reads_landing" {
  count = local.billing

  bucket = google_storage_bucket.landing[0].name
  role   = "roles/storage.objectViewer"
  member = google_service_account.dataflow_worker.member
}

# WriteToText writes a temporary object, copies it to its final name and deletes the first, so the
# quarantine needs delete; the condition keeps the events themselves out of the worker's reach
resource "google_storage_bucket_iam_member" "worker_writes_quarantine" {
  count = local.billing

  bucket = google_storage_bucket.landing[0].name
  role   = "roles/storage.objectUser"
  member = google_service_account.dataflow_worker.member

  condition {
    title       = "quarantine-only"
    description = "Only the objects under quarantine/."
    expression  = "resource.name.startsWith(\"projects/_/buckets/${google_storage_bucket.landing[0].name}/objects/quarantine/\")"
  }
}

# What submitting, following and cancelling a job takes. dataflow.developer also updates running jobs and
# manages snapshots. A job runs as whatever account the launcher may act as: dataflow-worker only (below).
resource "google_project_iam_custom_role" "dataflow_submitter" {
  count = local.billing

  project     = google_project.ingest.project_id
  role_id     = "vortexDataflowSubmitter"
  title       = "Vortex Dataflow submitter"
  description = "Submits, follows and cancels Dataflow jobs."
  permissions = [
    "dataflow.jobs.cancel",
    "dataflow.jobs.create", # NOSONAR(terraform:S6408) submitting is this permission, and the job runs only as dataflow-worker, the one account the launcher may act as; accepted risk
    "dataflow.jobs.get",
    "dataflow.jobs.list",
    "dataflow.messages.list",
    "dataflow.metrics.get",
  ]
}

resource "google_project_iam_member" "launcher_submits" {
  count = local.billing

  project = google_project.ingest.project_id
  role    = google_project_iam_custom_role.dataflow_submitter[0].id
  member  = google_service_account.pipeline_launcher.member
}

resource "google_service_account_iam_member" "launcher_acts_as_worker" {
  count = local.billing

  service_account_id = google_service_account.dataflow_worker.name
  role               = "roles/iam.serviceAccountUser"
  member             = google_service_account.pipeline_launcher.member
}

# Staging a file reads the bucket first (storage.buckets.get), which no object role carries
resource "google_storage_bucket_iam_member" "launcher_temp" {
  for_each = local.sandbox ? toset([]) : toset(["roles/storage.objectUser", "roles/storage.legacyBucketReader"])

  bucket = google_storage_bucket.dataflow_temp[0].name
  role   = each.value
  member = google_service_account.pipeline_launcher.member
}

resource "google_storage_bucket_iam_member" "launcher_landing" {
  count = local.billing

  bucket = google_storage_bucket.landing[0].name
  role   = "roles/storage.objectUser"
  member = google_service_account.pipeline_launcher.member
}
