# Kill-switch of the ingestion project, the one where Dataflow spends (ADR 0008, amendment of
# 2026-11-23): the budget publishes its cost to a topic, and a Cloud Run function unlinks billing
# from this project once the month's cost reaches kill_switch_amount. The analytics project holds
# views only and keeps its billing. Linking billing back is a human decision, taken with gcloud
# billing projects link: the provider keeps billing_account in the state after an unlink, so the
# plan shows no drift (infra/terraform/README.md).

locals {
  kill_switch_source = "${path.module}/../../../../data/kill-switch"

  # The budget notifications come from this Google account, outside the organization's domain
  budget_publisher = "serviceAccount:billing-budget-alert@system.gserviceaccount.com"
}

resource "google_pubsub_topic" "budget" {
  #checkov:skip=CKV_GCP_83:Carries only cost figures, encrypted with Google-managed keys; a Cloud KMS key would bill every month to guard numbers that are not secret
  count = local.billing

  project = google_project.ingest.project_id
  name    = "billing-budget-alerts"

  depends_on = [google_project_service.ingest_billing]
}

# Domain restricted sharing. The organization enforces the legacy iam.allowedPolicyMemberDomains
# (its own customer id only), which takes no per-principal exception. On this project only, the
# managed iam.managed.allowedPolicyMembers takes over: the organization's principal set (its users,
# the service accounts and service agents of its projects) plus the budget publisher, nobody else.
# The managed one goes first, so the project is never left without a restriction.
resource "google_org_policy_policy" "ingest_allowed_members" {
  provider = google.billing
  count    = local.billing

  name   = "projects/${google_project.ingest.number}/policies/iam.managed.allowedPolicyMembers"
  parent = "projects/${google_project.ingest.number}"

  spec {
    rules {
      enforce = "TRUE"
      parameters = jsonencode({
        allowedMemberSubjects = [local.budget_publisher]
        allowedPrincipalSets  = ["//cloudresourcemanager.googleapis.com/organizations/${var.org_id}"]
      })
    }
  }

  depends_on = [google_project_service.ingest_billing]
}

resource "google_org_policy_policy" "ingest_legacy_domains" {
  provider = google.billing
  count    = local.billing

  name   = "projects/${google_project.ingest.number}/policies/iam.allowedPolicyMemberDomains"
  parent = "projects/${google_project.ingest.number}"

  spec {
    rules {
      allow_all = "TRUE"
    }
  }

  depends_on = [google_org_policy_policy.ingest_allowed_members]
}

resource "google_pubsub_topic_iam_member" "budget_publishes" {
  count = local.billing

  project = google_project.ingest.project_id
  topic   = google_pubsub_topic.budget[0].name
  role    = "roles/pubsub.publisher"
  member  = local.budget_publisher

  depends_on = [google_org_policy_policy.ingest_legacy_domains]
}

# Runs the function and receives its trigger. Project Billing Manager unlinks billing and Browser
# reads the project's billing info; no role on the billing account (the official sample grants
# billing.admin on it). If the real test is refused, Billing Account Viewer is the next step.
resource "google_service_account" "kill_switch" {
  count = local.billing

  project      = google_project.ingest.project_id
  account_id   = "billing-kill-switch"
  display_name = "Vortex billing kill-switch"
  description  = "Unlinks billing from the ingestion project when the budget reports kill_switch_amount; also invokes the function from its trigger."
}

resource "google_project_iam_member" "kill_switch_unlinks_billing" {
  for_each = local.sandbox ? toset([]) : toset(["roles/billing.projectManager", "roles/browser"])

  project = google_project.ingest.project_id
  role    = each.value
  member  = google_service_account.kill_switch[0].member
}

# Cloud Build runs as this account: new organizations no longer grant Editor to the default ones
resource "google_service_account" "function_builder" {
  count = local.billing

  project      = google_project.ingest.project_id
  account_id   = "function-builder"
  display_name = "Vortex function builder"
  description  = "Builds the kill-switch function: reads its source, writes the image and the build logs."
}

resource "google_project_iam_member" "builder" {
  for_each = local.sandbox ? toset([]) : toset(["roles/logging.logWriter", "roles/artifactregistry.writer"])

  project = google_project.ingest.project_id
  role    = each.value
  member  = google_service_account.function_builder[0].member
}

resource "google_storage_bucket" "function_source" {
  #checkov:skip=CKV_GCP_62:Holds only the zipped function source, already in git; access logs would cost more than what they protect
  #checkov:skip=CKV_GCP_78:Each upload has its own name (the zip's MD5), so versioning would keep only copies of git history
  count = local.billing

  project                     = google_project.ingest.project_id
  name                        = "${google_project.ingest.project_id}-functions"
  location                    = var.region
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  lifecycle_rule {
    condition {
      age = 30
    }
    action {
      type = "Delete"
    }
  }

  depends_on = [google_project_service.ingest_billing]
}

# Cloud Functions copies the zip into its own gcf-v2-sources bucket, created by Google on the first
# deploy, and the build reads it from there; a bucket-level grant cannot exist before that bucket
# does. So the grant sits on the project, with a condition that keeps it to the two source buckets
# (any future bucket of this project, such as Dataflow's, stays out of the builder's reach).
resource "google_project_iam_member" "builder_reads_sources" {
  count = local.billing

  project = google_project.ingest.project_id
  role    = "roles/storage.objectViewer"
  member  = google_service_account.function_builder[0].member

  condition {
    title       = "function-sources-only"
    description = "Only the kill-switch source bucket and the one Cloud Functions copies it into."
    expression = join(" || ", [
      "resource.name.startsWith(\"projects/_/buckets/${google_storage_bucket.function_source[0].name}\")",
      "resource.name.startsWith(\"projects/_/buckets/gcf-v2-sources-${google_project.ingest.number}-${var.region}\")",
    ])
  }
}

# Only main.py and requirements.txt (exported from uv.lock): the tests and the virtualenv stay out
data "archive_file" "kill_switch" {
  type        = "zip"
  output_path = "${path.module}/.terraform/kill-switch.zip"

  source {
    content  = file("${local.kill_switch_source}/main.py")
    filename = "main.py"
  }

  source {
    content  = file("${local.kill_switch_source}/requirements.txt")
    filename = "requirements.txt"
  }
}

resource "google_storage_bucket_object" "kill_switch" {
  count = local.billing

  bucket = google_storage_bucket.function_source[0].name
  name   = "kill-switch-${data.archive_file.kill_switch.output_md5}.zip"
  source = data.archive_file.kill_switch.output_path
}

resource "google_cloudfunctions2_function" "kill_switch" {
  count = local.billing

  project     = google_project.ingest.project_id
  location    = var.region
  name        = "billing-kill-switch"
  description = "Unlinks billing from ${google_project.ingest.project_id} once the month's cost reaches ${var.kill_switch_amount}."

  build_config {
    runtime         = "python312"
    entry_point     = "kill_switch"
    service_account = google_service_account.function_builder[0].id

    source {
      storage_source {
        bucket = google_storage_bucket.function_source[0].name
        object = google_storage_bucket_object.kill_switch[0].name
      }
    }
  }

  service_config {
    max_instance_count             = 1
    available_memory               = "256M"
    timeout_seconds                = 60
    ingress_settings               = "ALLOW_INTERNAL_ONLY"
    all_traffic_on_latest_revision = true
    service_account_email          = google_service_account.kill_switch[0].email

    environment_variables = {
      TARGET_PROJECT     = google_project.ingest.project_id
      KILL_SWITCH_AMOUNT = tostring(var.kill_switch_amount)
    }
  }

  # A failed run is not retried: the budget publishes again within hours anyway
  event_trigger {
    trigger_region        = var.region
    event_type            = "google.cloud.pubsub.topic.v1.messagePublished"
    pubsub_topic          = google_pubsub_topic.budget[0].id
    retry_policy          = "RETRY_POLICY_DO_NOT_RETRY"
    service_account_email = google_service_account.kill_switch[0].email
  }

  depends_on = [
    google_project_iam_member.builder,
    google_project_iam_member.builder_reads_sources,
  ]
}

# The trigger calls the function's Cloud Run service as the kill-switch account
resource "google_cloud_run_v2_service_iam_member" "kill_switch_invokes" {
  count = local.billing

  project  = google_project.ingest.project_id
  location = var.region
  name     = google_cloudfunctions2_function.kill_switch[0].name
  role     = "roles/run.invoker"
  member   = google_service_account.kill_switch[0].member
}
