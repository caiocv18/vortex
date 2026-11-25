output "ingest_project_id" {
  description = "Project that receives the events and holds the raw and curated datasets."
  value       = google_project.ingest.project_id
}

output "analytics_project_id" {
  description = "Project of the authorized views, where analysts run and pay for their queries."
  value       = google_project.analytics.project_id
}

output "pipeline_launcher_email" {
  description = "Service account that appends a local run's output (data/scripts/sandbox.sh load) and submits the Dataflow jobs."
  value       = google_service_account.pipeline_launcher.email
}

output "analyst_reader_email" {
  description = "Service account of the analyst (data/scripts/sandbox.sh prove)."
  value       = google_service_account.analyst_reader.email
}

output "dataflow_worker_email" {
  description = "Service account of the Dataflow workers."
  value       = google_service_account.dataflow_worker.email
}

output "sandbox" {
  description = "True while no billing account is linked: partitions expire by event date (30 days raw, 60 curated) and views on a fixed date."
  value       = local.sandbox
}

output "budget_topic" {
  description = "Pub/Sub topic of the budget, read by the kill-switch; publish a simulated notification here to test it. Null in the sandbox."
  value       = local.sandbox ? null : google_pubsub_topic.budget[0].id
}

output "kill_switch_function" {
  description = "Cloud Run function that unlinks billing from the ingestion project. Null in the sandbox."
  value       = local.sandbox ? null : google_cloudfunctions2_function.kill_switch[0].name
}

output "pii_policy_tag" {
  description = "Policy tag on the personal columns of the raw tables. Null in the sandbox."
  value       = local.sandbox ? null : google_data_catalog_policy_tag.pii[0].name
}

output "landing_bucket" {
  description = "Bucket of the events a Dataflow job reads and of its quarantine. Null in the sandbox."
  value       = local.sandbox ? null : google_storage_bucket.landing[0].name
}

output "dataflow_temp_bucket" {
  description = "Staging and temporary bucket of the Dataflow jobs. Null in the sandbox."
  value       = local.sandbox ? null : google_storage_bucket.dataflow_temp[0].name
}

output "dataflow_subnetwork" {
  description = "Subnetwork of the Dataflow workers, as --subnetwork takes it. Null in the sandbox."
  value       = local.sandbox ? null : "regions/${var.region}/subnetworks/${google_compute_subnetwork.dataflow[0].name}"
}

output "hmac_secret" {
  description = "Secret that holds the HMAC key; its version is added by hand. Null in the sandbox."
  value       = local.sandbox ? null : google_secret_manager_secret.hmac_key[0].id
}
