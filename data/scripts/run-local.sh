#!/usr/bin/env bash
# Runs the pipeline on the DirectRunner and writes one NDJSON file per table to data/out/<run id>/.
# Usage: data/scripts/run-local.sh [MOVEMENTS_GLOB] [USER_EVENTS_GLOB]
# Without arguments it reads the test fixtures. SOURCE_TIMEZONE sets the zone of the inventory app's
# date-times, which carry no offset (default America/Sao_Paulo).
set -euo pipefail

data_dir=$(cd "$(dirname "$0")/.." && pwd)
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

uv run --frozen --project "$data_dir/pipeline" python -m vortex_pipeline \
  --runner=DirectRunner \
  --movements_input="$movements" \
  --user_events_input="$user_events" \
  --output_dir="$output" \
  --source_timezone="${SOURCE_TIMEZONE:-America/Sao_Paulo}" \
  --run_id="$run_id"

wc -l "$output"/*.ndjson "$output"/quarantine/*.ndjson
