#!/usr/bin/env bash
# Scenario B: the pipeline as a Dataflow job, against the stack of infra/terraform/gcp/data with billing.
# Everything runs as pipeline-launcher, except `views`, which runs as analyst-reader.
#   upload               copies the test fixtures to the landing bucket (fixtures/)
#   run [--prove-cutoff] builds the wheel, shows the options, asks for confirmation and submits ONE PAID JOB;
#                        waits for it, then empties the temporary bucket. --prove-cutoff lowers the runtime
#                        limit from 1800 to 120 seconds, so Dataflow must cancel the job: exits 1
#   verify RUN_ID        runs the fixtures locally and compares: rows per table, curated content, quarantine
#   views                rows and SHA-256 of each analytics view
#   clean                empties the temporary bucket; exits 1 if anything is left or a job is active
# A job that failed half way may have loaded some tables, and a new run appends again: never resubmit in a
# loop. Needs Application Default Credentials of the operator with a quota project, and gcloud logged in.
# The names come from the stack's outputs (AWS_PROFILE for the state).
set -euo pipefail
export LC_ALL=C

data_dir=$(cd "$(dirname "$0")/.." && pwd)
stack="$data_dir/../infra/terraform/gcp/data"
region=us-central1
output() {
  local name=$1
  terraform -chdir="$stack" output -raw "$name"
}
pipeline() { uv run --frozen --project "$data_dir/pipeline" python -m "$@"; }
die() {
  echo "dataflow: $1" >&2
  exit 1
}

load_outputs() {
  ingest=$(output ingest_project_id)
  launcher=$(output pipeline_launcher_email)
  landing=$(output landing_bucket)
  temp=$(output dataflow_temp_bucket)
  [[ -n "$landing" && -n "$temp" ]] || die "the stack has no buckets: no billing account"
}
as_launcher() { gcloud --quiet --project="$ingest" --impersonate-service-account="$launcher" "$@"; }

# Objects of the temporary bucket, one per line. gcloud exits 1 on an empty bucket, as it does on a refusal,
# so only its message tells the two apart.
temp_objects() {
  local errors status=0
  errors=$(mktemp)
  as_launcher storage ls "gs://$temp/**" 2>"$errors" || status=$?
  if [[ $status -ne 0 ]] && ! grep -q 'matched no objects' "$errors"; then
    cat "$errors" >&2
    rm -f "$errors"
    return 1
  fi
  rm -f "$errors"
}

empty_temp() {
  local left
  left=$(temp_objects) || return 1
  if [[ -n "$left" ]]; then
    as_launcher storage rm "gs://$temp/**" || return 1
    left=$(temp_objects) || return 1
  fi
  if [[ -n "$left" ]]; then
    echo "dataflow: objects left in gs://$temp" >&2
    return 1
  fi
  echo "dataflow: gs://$temp is empty"
}

job_state() { as_launcher dataflow jobs describe "$1" --region="$region" --format='value(currentState)' 2>/dev/null; }

# Waits until the job can no longer write, cancelling it first. The launcher ends before the job on Ctrl-C,
# on a lost credential and while the job is still being cancelled. gcloud exits 0 on a refused cancel, so it
# asks again on every turn.
settle() {
  local job=$1 state
  for _ in {1..60}; do
    state=$(job_state "$job") || state=UNREADABLE
    case "$state" in
      JOB_STATE_DONE | JOB_STATE_FAILED | JOB_STATE_CANCELLED)
        echo "dataflow: job $job ended in $state"
        return 0
        ;;
      JOB_STATE_RUNNING | JOB_STATE_PENDING | JOB_STATE_QUEUED)
        echo "dataflow: the launcher is gone and job $job is in $state; cancelling" >&2
        as_launcher dataflow jobs cancel "$job" --region="$region" || true
        ;;
    esac
    sleep 10
  done
  echo "dataflow: job $job still in $state; NOT cleaning. Check it, then run: $0 clean" >&2
  return 1
}

# What the service recorded about the job. It must come before the cleaning: --full reads the staged
# pipeline.pb, and after it the describe fails.
describe() {
  local attempt
  for attempt in 1 2 3; do
    if describe_once "$1"; then return 0; fi
    echo "dataflow: can't describe job $1 (attempt $attempt)" >&2
  done
  return 1
}

describe_once() {
  as_launcher dataflow jobs describe "$1" --region="$region" --full --format=json |
    jq '{currentState, createTime, currentStateTime, serviceAccount: .environment.serviceAccountEmail,
      serviceOptions: .environment.serviceOptions,
      workerPools: [.environment.workerPools[]? | {kind, numWorkers, machineType, diskSizeGb, subnetwork,
        ipConfiguration, autoscalingSettings}]}'
}

# EXIT trap of `run`: never hides the launcher's own exit code, and a second Ctrl-C can't stop it
finish() {
  local launcher_code=$? code job
  trap - EXIT
  trap '' INT TERM HUP
  code=$launcher_code
  echo "dataflow: settling the job, then cleaning; don't close the terminal" >&2
  job=$(sed -n 's/.*Created job with id: \[\([^]]*\)\].*/\1/p' "$work/launcher.log" | head -n 1)
  if [[ -z "$job" ]]; then
    job=$(as_launcher dataflow jobs list --region="$region" --filter="name=$job_name" \
      --format='value(id)' 2>/dev/null) || {
      echo "dataflow: can't tell whether a job named $job_name was created; NOT cleaning. Check, then run: $0 clean" >&2
      rm -rf "$work"
      [[ $code -ne 0 ]] || code=1
      exit "$code"
    }
  fi
  # A failed describe does not keep personal data in the bucket: it cleans anyway, and the run exits 1
  if [[ -z "$job" ]] || settle "$job"; then
    if [[ -n "$job" ]] && ! describe "$job"; then [[ $code -ne 0 ]] || code=1; fi
    if ! empty_temp; then [[ $code -ne 0 ]] || code=1; fi
  else
    [[ $code -ne 0 ]] || code=1
  fi
  echo "dataflow: run_id=$job_name job_id=${job:-none} launcher exit code=$launcher_code, exit code=$code"
  rm -rf "$work"
  exit "$code"
}

run() {
  local walltime=1800 answer wheel subnetwork worker secret
  case "${1:-}" in
    "") ;;
    --prove-cutoff) walltime=120 ;;
    *) die "run takes only --prove-cutoff" ;;
  esac
  command -v jq >/dev/null || die "run needs jq"
  load_outputs
  subnetwork=$(output dataflow_subnetwork)
  worker=$(output dataflow_worker_email)
  secret=$(output hmac_secret)
  # Also the run id: lower case, as a job name must be
  job_name="vortex-$(date -u +%Y%m%dt%H%M%Sz)"
  work=$(mktemp -d)
  uv build --quiet --wheel --project "$data_dir/pipeline" --out-dir "$work" "$data_dir/pipeline"
  cd "$work"
  wheel=$(ls ./*.whl)
  local options=(
    --runner=DataflowRunner
    --project="$ingest"
    --region="$region"
    --job_name="$job_name"
    --run_id="$job_name"
    --movements_input="gs://$landing/fixtures/movements.ndjson"
    --user_events_input="gs://$landing/fixtures/user_events.ndjson"
    --output_dir="gs://$landing"
    --source_timezone=America/Sao_Paulo
    --bigquery_project="$ingest"
    --hmac_secret="$secret/versions/latest"
    --temp_location="gs://$temp/tmp"
    --staging_location="gs://$temp/staging"
    --num_workers=1
    --max_num_workers=1
    --autoscaling_algorithm=NONE
    --machine_type=n1-standard-1
    --disk_size_gb=30
    --dataflow_service_options="max_workflow_runtime_walltime_seconds=$walltime"
    --subnetwork="$subnetwork"
    --use_public_ips
    --service_account_email="$worker"
    --impersonate_service_account="$launcher"
    --extra_package="$wheel"
    --experiments=disable_logging_submission_environment
  )
  printf '  %s\n' "${options[@]}"
  read -r -p "Submit this paid job? Type run: " answer || answer=""
  if [[ "$answer" != run ]]; then
    rm -rf "$work"
    die "not submitted"
  fi
  : >"$work/launcher.log"
  trap finish EXIT
  # Without these, finish sees 0 after a signal
  trap 'exit 130' INT
  trap 'exit 143' TERM
  trap 'exit 129' HUP
  # The options must reach Python as its command line: impersonation set in code is lost
  pipeline vortex_pipeline "${options[@]}" 2>&1 | tee "$work/launcher.log"
}

tagged() {
  local name=$1
  sed "s/^/$name\t/"
}

verify() {
  local run_id=${1:-} local_run table
  [[ "$run_id" =~ ^[a-z0-9][a-z0-9-]{0,62}$ ]] || die "verify needs the run id of a job"
  load_outputs
  "$data_dir/scripts/run-local.sh"
  local_run=$(find "$data_dir/out" -mindepth 1 -maxdepth 1 -type d -name 'local-*' | sort | tail -n 1)
  {
    for table in raw_movements curated_movements raw_user_events curated_user_events; do
      tagged "$table" <"$local_run/$table.ndjson"
    done
    tagged rejected <"$local_run/quarantine/rejected.ndjson"
    as_launcher storage cat "gs://$landing/quarantine/$run_id/rejected.ndjson" | tagged quarantine
  } | pipeline vortex_pipeline.dataflow_check verify --run-id "$run_id" --project "$ingest" \
    --impersonate "$launcher"
}

case "${1:-}" in
  upload)
    load_outputs
    for name in movements user_events; do
      as_launcher storage cp "$data_dir/pipeline/tests/fixtures/$name.ndjson" "gs://$landing/fixtures/$name.ndjson"
    done
    ;;
  run)
    run "${@:2}"
    ;;
  verify)
    verify "${2:-}"
    ;;
  views)
    pipeline vortex_pipeline.dataflow_check views --project "$(output analytics_project_id)" \
      --impersonate "$(output analyst_reader_email)"
    ;;
  clean)
    load_outputs
    active=$(as_launcher dataflow jobs list --region="$region" --status=active --format='value(id)')
    [[ -z "$active" ]] || die "job active, not cleaning: $active"
    empty_temp
    ;;
  *)
    echo "usage: $0 upload | run [--prove-cutoff] | verify RUN_ID | views | clean" >&2
    exit 2
    ;;
esac
