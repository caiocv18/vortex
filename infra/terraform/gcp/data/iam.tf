# Dataset grants go through google_bigquery_dataset_access, never through
# google_bigquery_dataset_iam_*: those rewrite the dataset's access list and drop
# the authorized dataset entry that lets the analytics views read vortex_curated.
# Basic roles (READER, WRITER) because the API swaps the predefined ones for them,
# which would show as a diff on every plan.

resource "google_service_account" "pipeline_launcher" {
  project      = google_project.ingest.project_id
  account_id   = "pipeline-launcher"
  display_name = "Vortex pipeline launcher"
  description  = "Appends a local run's output to vortex_raw and vortex_curated, and submits the Dataflow jobs."

  depends_on = [google_project_service.ingest]
}

# Identity of the Dataflow workers; its grants are in dataflow_iam.tf
resource "google_service_account" "dataflow_worker" {
  project      = google_project.ingest.project_id
  account_id   = "dataflow-worker"
  display_name = "Vortex Dataflow worker"
  description  = "Runs the Dataflow workers of the Vortex pipeline."

  depends_on = [google_project_service.ingest]
}

resource "google_service_account" "analyst_reader" {
  project      = google_project.analytics.project_id
  account_id   = "analyst-reader"
  display_name = "Vortex analyst (read only)"
  description  = "Reads the views of vortex_analytics and nothing else; its queries are billed to the analytics project."

  depends_on = [google_project_service.analytics]
}

resource "google_bigquery_dataset_access" "launcher_writes" {
  for_each = {
    raw     = google_bigquery_dataset.raw.dataset_id
    curated = google_bigquery_dataset.curated.dataset_id
  }

  project       = google_project.ingest.project_id
  dataset_id    = each.value
  role          = "WRITER"
  user_by_email = google_service_account.pipeline_launcher.email
}

resource "google_project_iam_member" "launcher_runs_jobs" {
  project = google_project.ingest.project_id
  role    = "roles/bigquery.jobUser"
  member  = google_service_account.pipeline_launcher.member
}

# Authorized dataset: every view in vortex_analytics may read vortex_curated, and
# whoever can read those views gets nothing more. Only Terraform creates views there.
resource "google_bigquery_dataset_access" "analytics_views_read_curated" {
  project    = google_project.ingest.project_id
  dataset_id = google_bigquery_dataset.curated.dataset_id

  dataset {
    dataset {
      project_id = google_project.analytics.project_id
      dataset_id = google_bigquery_dataset.analytics.dataset_id
    }
    target_types = ["VIEWS"]
  }
}

resource "google_bigquery_dataset_access" "analyst_reads_views" {
  project       = google_project.analytics.project_id
  dataset_id    = google_bigquery_dataset.analytics.dataset_id
  role          = "READER"
  user_by_email = google_service_account.analyst_reader.email
}

resource "google_project_iam_member" "analyst_runs_jobs" {
  project = google_project.analytics.project_id
  role    = "roles/bigquery.jobUser"
  member  = google_service_account.analyst_reader.member
}

# The operator acts as each account through short-lived tokens (impersonation),
# since the organization forbids service account keys.
resource "google_service_account_iam_member" "operator_impersonates" {
  for_each = {
    launcher = google_service_account.pipeline_launcher.name
    analyst  = google_service_account.analyst_reader.name
  }

  service_account_id = each.value
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = var.operator_member
}
