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
  description = "True while no billing account is linked: tables and partitions expire in 60 days."
  value       = local.sandbox
}
