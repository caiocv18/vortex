#!/usr/bin/env bash
# Scenario A, against the BigQuery sandbox created by infra/terraform/gcp/data.
#   load [OUTPUT_DIR]  appends a local run's files as pipeline-launcher (default: the newest data/out/*)
#   prove              counts the rows, then reads the authorized view as analyst-reader and checks
#                      that both base tables refuse it; exits 1 if the analyst can read a base table
# Needs Application Default Credentials of the operator (gcloud auth application-default login),
# who holds Service Account Token Creator on both service accounts. The project ids and account
# emails come from the stack's outputs unless VORTEX_INGEST_PROJECT, VORTEX_ANALYTICS_PROJECT,
# VORTEX_LAUNCHER_SA and VORTEX_ANALYST_SA are set.
set -euo pipefail

data_dir=$(cd "$(dirname "$0")/.." && pwd)
stack="$data_dir/../infra/terraform/gcp/data"
output() {
  local name=$1
  terraform -chdir="$stack" output -raw "$name"
}
sandbox() { uv run --frozen --project "$data_dir/pipeline" python -m vortex_pipeline.bigquery_sandbox "$@"; }

newest_run() {
  [[ -d "$data_dir/out" ]] && find "$data_dir/out" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1
}

case "${1:-}" in
  load)
    run_dir=${2:-$(newest_run || true)}
    if [[ -z "$run_dir" || ! -d "$run_dir" ]]; then
      echo "sandbox: no run to load; run data/scripts/run-local.sh first" >&2
      exit 1
    fi
    echo "sandbox: loading $run_dir"
    sandbox load --output-dir "$run_dir" --project "${VORTEX_INGEST_PROJECT:-$(output ingest_project_id)}" \
      --impersonate "${VORTEX_LAUNCHER_SA:-$(output pipeline_launcher_email)}"
    ;;
  prove)
    sandbox prove --ingest-project "${VORTEX_INGEST_PROJECT:-$(output ingest_project_id)}" \
      --analytics-project "${VORTEX_ANALYTICS_PROJECT:-$(output analytics_project_id)}" \
      --analyst "${VORTEX_ANALYST_SA:-$(output analyst_reader_email)}"
    ;;
  *)
    echo "usage: $0 load [OUTPUT_DIR] | prove" >&2
    exit 2
    ;;
esac
