# Cost guardrails, created only with billing_account (ADR 0008, amendment of 2026-11-23).
# The budget covers both projects and counts the gross cost: credits are excluded, so a Free Trial
# alerts as if it were paid. Cloud Billing has no spend cap for BigQuery, Dataflow or Cloud Storage,
# so the cap is the kill-switch in kill_switch.tf, fed by this budget's Pub/Sub topic.

locals {
  billing = local.sandbox ? 0 : 1

  budget_projects = [
    "projects/${google_project.ingest.number}",
    "projects/${google_project.analytics.number}",
  ]
}

# The Budget and Org Policy APIs refuse user credentials without a quota project, so their calls
# carry the ingestion project in X-Goog-User-Project. Only the budgets, the org policies of
# kill_switch.tf and the policy tag of policy_tags.tf use this provider.
provider "google" {
  alias                 = "billing"
  region                = var.region
  billing_project       = google_project.ingest.project_id
  user_project_override = true
}

# Essential Contacts refuses an address outside the organization's domain; a Monitoring e-mail
# channel takes any address and is what a budget notifies besides its IAM recipients
resource "google_monitoring_notification_channel" "budget_email" {
  count = local.billing

  project      = google_project.ingest.project_id
  display_name = "Vortex budget alerts"
  type         = "email"
  labels = {
    email_address = var.alert_email
  }

  lifecycle {
    precondition {
      condition     = var.alert_email != null
      error_message = "Set alert_email in terraform.tfvars together with billing_account."
    }
  }

  depends_on = [google_project_service.ingest_billing]
}

resource "google_billing_budget" "monthly" {
  count    = local.billing
  provider = google.billing

  billing_account = var.billing_account
  display_name    = "vortex-gcp-monthly"

  budget_filter {
    projects               = local.budget_projects
    calendar_period        = "MONTH"
    credit_types_treatment = "EXCLUDE_ALL_CREDITS"
  }

  amount {
    specified_amount {
      units = tostring(floor(var.budget_amount))
      nanos = floor((var.budget_amount - floor(var.budget_amount)) * 1000000000)
    }
  }

  dynamic "threshold_rules" {
    for_each = [0.5, 0.9, 1.0]
    content {
      threshold_percent = threshold_rules.value
      spend_basis       = "CURRENT_SPEND"
    }
  }

  # The topic receives the cost several times a day, threshold or not; the kill-switch reads it
  all_updates_rule {
    monitoring_notification_channels = [google_monitoring_notification_channel.budget_email[0].id]
    disable_default_iam_recipients   = true
    pubsub_topic                     = google_pubsub_topic.budget[0].id
    schema_version                   = "1.0"
  }

  depends_on = [google_pubsub_topic_iam_member.budget_publishes]
}

# Proves the e-mail: Cloud Billing has no way to send a budget alert on demand, so this 1-unit
# budget on the ingestion project alerts at 1% of it, on the first gross cent. Turn it off after.
resource "google_billing_budget" "email_test" {
  count    = local.billing == 1 && var.email_test_budget ? 1 : 0
  provider = google.billing

  billing_account = var.billing_account
  display_name    = "vortex-email-test"

  budget_filter {
    projects               = ["projects/${google_project.ingest.number}"]
    calendar_period        = "MONTH"
    credit_types_treatment = "EXCLUDE_ALL_CREDITS"
  }

  amount {
    specified_amount {
      units = "1"
    }
  }

  threshold_rules {
    threshold_percent = 0.01
    spend_basis       = "CURRENT_SPEND"
  }

  all_updates_rule {
    monitoring_notification_channels = [google_monitoring_notification_channel.budget_email[0].id]
    disable_default_iam_recipients   = true
  }

  depends_on = [google_project_service.ingest_billing]
}
