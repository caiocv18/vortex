#!/usr/bin/env bash
# Runs the pipeline on the DirectRunner and writes one NDJSON file per table to data/out/<run id>/.
# Usage: data/scripts/run-local.sh [--rebase-to-today] [MOVEMENTS_FILE] [USER_EVENTS_FILE]
# Without file arguments it reads the test fixtures. --rebase-to-today first shifts every date-time of both
# files by one delta, so the newest event lands now (vortex_pipeline.rebase): the BigQuery partitions
# expire by event date, and this lets an old capture (data/landing/<date>/) be loaded again.
# SOURCE_TIMEZONE sets the zone of the inventory app's date-times, which carry no offset
# (default America/Sao_Paulo).
set -euo pipefail

data_dir=$(cd "$(dirname "$0")/.." && pwd)
rebase=false
if [[ "${1:-}" == "--rebase-to-today" ]]; then
  rebase=true
  shift
fi
fixtures="$data_dir/pipeline/tests/fixtures"
movements=${1:-$fixtures/movements.ndjson}
user_events=${2:-$fixtures/user_events.ndjson}

if [[ ! -f "$data_dir/.env" ]]; then
  echo "run-local: no HMAC key; run data/scripts/generate-hmac-key.sh first" >&2
  exit 1
fi
set -a
# shellcheck source=/dev/null
source "$data_dir/.env"
set +a

run_id="local-$(date -u +%Y%m%dT%H%M%SZ)"
output="$data_dir/out/$run_id"

if [[ "$rebase" == true ]]; then
  # The module is a stdin/stdout filter; the files stay in the run's own folder, ignored by git like data/out
  rebase() {
    uv run --frozen --project "$data_dir/pipeline" python -m vortex_pipeline.rebase \
      --source-timezone "${SOURCE_TIMEZONE:-America/Sao_Paulo}" "$@"
  }
  newest=$(cat "$movements" "$user_events" | rebase newest)
  mkdir -p "$output/rebased"
  rebase shift --newest "$newest" <"$movements" >"$output/rebased/movements.ndjson"
  rebase shift --newest "$newest" <"$user_events" >"$output/rebased/user_events.ndjson"
  echo "run-local: rebased both files so the newest event ($newest) lands now"
  movements="$output/rebased/movements.ndjson"
  user_events="$output/rebased/user_events.ndjson"
fi

uv run --frozen --project "$data_dir/pipeline" python -m vortex_pipeline \
  --runner=DirectRunner \
  --movements_input="$movements" \
  --user_events_input="$user_events" \
  --output_dir="$output" \
  --source_timezone="${SOURCE_TIMEZONE:-America/Sao_Paulo}" \
  --run_id="$run_id"

wc -l "$output"/*.ndjson "$output"/quarantine/*.ndjson
