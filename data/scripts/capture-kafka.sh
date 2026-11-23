#!/usr/bin/env bash
# Drains the two topics the pipeline reads, from the first offset to the end offset measured at start,
# into landing files: data/landing/<YYYY-MM-DD>/movements.ndjson and user_events.ndjson.
# The broker image (apache/kafka-native) ships no CLI tools, so the consumer runs in a throwaway
# apache/kafka container of the same version, on the host network.
# Refuses to keep a capture holding any e-mail outside example.com: only fictitious people go to landing.
# Env: KAFKA_BOOTSTRAP (default localhost:9092), KAFKA_TOOLS_IMAGE (default apache/kafka:4.4.0).
# Usage: data/scripts/capture-kafka.sh [LANDING_DIR]
set -euo pipefail

data_dir=$(cd "$(dirname "$0")/.." && pwd)
landing=${1:-$data_dir/landing/$(date +%F)}
bootstrap=${KAFKA_BOOTSTRAP:-localhost:9092}
image=${KAFKA_TOOLS_IMAGE:-apache/kafka:4.4.0}

kafka() {
  local tool=$1
  shift
  docker run --rm --network host --entrypoint "/opt/kafka/bin/$tool" "$image" "$@"
}

drain() {
  local topic=$1 target=$2 end
  # One line per partition, topic:partition:end-offset; the sum is how many records to read
  end=$(kafka kafka-get-offsets.sh --bootstrap-server "$bootstrap" --topic "$topic" --time latest |
    awk -F: '{ total += $3 } END { print total + 0 }')
  if [[ "$end" -eq 0 ]]; then
    echo "capture-kafka: $topic is empty; run data/scripts/seed-api.sh against a stack started with -m kafka" >&2
    return 1
  fi
  kafka kafka-console-consumer.sh --bootstrap-server "$bootstrap" --topic "$topic" --from-beginning \
    --max-messages "$end" --timeout-ms 30000 >"$target" 2>/dev/null || true
  if [[ $(wc -l <"$target") -ne "$end" ]]; then
    echo "capture-kafka: $topic gave $(wc -l <"$target") of $end records" >&2
    return 1
  fi
}

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
drain vortex.movimento.estoque "$work/movements.ndjson"
drain vortex-user-events "$work/user_events.ndjson"

outsiders=$(cat "$work"/*.ndjson | grep -oE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]+' |
  grep -vc '@example\.com$' || true)
if [[ "$outsiders" -ne 0 ]]; then
  echo "capture-kafka: $outsiders e-mail(s) outside example.com; nothing written" >&2
  exit 1
fi

mkdir -p "$landing"
mv "$work/movements.ndjson" "$work/user_events.ndjson" "$landing/"
wc -l "$landing"/*.ndjson
