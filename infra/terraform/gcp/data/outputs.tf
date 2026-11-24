output "ingest_project_id" {
  description = "Project that receives the events and holds the raw and curated datasets."
  value       = google_project.ingest.project_id
}

output "analytics_project_id" {
  description = "Project of the authorized views, where analysts run and pay for their queries."
  value       = google_project.analytics.project_id
}

output "pipeline_launcher_email" {
  description = "Service account that appends the pipeline's output (data/scripts/sandbox.sh load)."
  value       = google_service_account.pipeline_launcher.email
}

output "analyst_reader_email" {
  description = "Service account of the analyst (data/scripts/sandbox.sh prove)."
  value       = google_service_account.analyst_reader.email
}

output "dataflow_worker_email" {
  description = "Service account of the Dataflow workers, with no role until the Dataflow block."
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
