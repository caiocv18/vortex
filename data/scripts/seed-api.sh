#!/usr/bin/env bash
# Creates a small, fictitious sample through the APIs of a running Vortex (-m kafka), so both producers
# publish real events: three users sign up at the authorization service (<name>-<stamp>@example.com),
# each one records an ENTRADA and a SAIDA in the main app with its own token, then signs in once more.
# Passwords are random, kept in memory only and never printed. Capture the events afterwards with
# data/scripts/capture-kafka.sh.
# Env: VORTEX_AUTH_URL (default http://localhost:8081), VORTEX_APP_URL (default http://localhost:8080).
set -euo pipefail

auth=${VORTEX_AUTH_URL:-http://localhost:8081}
app=${VORTEX_APP_URL:-http://localhost:8080}
stamp=$(date -u +%m%d%H%M%S)

# POST a JSON body read from stdin, so neither the body nor the token shows up in the process list
post() {
  local url=$1 token=${2:-}
  if [[ -n "$token" ]]; then
    curl -fsS -X POST "$url" -H 'Content-Type: application/json' -H @<(printf 'Authorization: Bearer %s\n' "$token") \
      --data-binary @-
  else
    curl -fsS -X POST "$url" -H 'Content-Type: application/json' --data-binary @-
  fi
}

credentials() { jq -n --arg u "$1" --arg p "$2" "$3"; }

product_ids=()
i=0
for name in alice bob carol; do
  user="$name-$stamp"
  # Random hex plus one of each class the authorization service's password policy requires
  password="$(openssl rand -hex 16)Aa1!"
  token=$(credentials "$user" "$password" \
    '{email: ($u + "@example.com"), username: $u, password: $p, confirmPassword: $p}' |
    post "$auth/api/auth/register" | jq -r .data.accessToken)

  if [[ ${#product_ids[@]} -eq 0 ]]; then
    mapfile -t product_ids < <(curl -fsS "$app/api/produtos?size=3&sort=id,asc" \
      -H @<(printf 'Authorization: Bearer %s\n' "$token") | jq -r '.content[].id')
    if [[ ${#product_ids[@]} -eq 0 ]]; then
      echo "seed-api: no product to move; is the main app up with its demo data?" >&2
      exit 1
    fi
  fi
  product=${product_ids[$((i % ${#product_ids[@]}))]}
  i=$((i + 1))

  for move in 'ENTRADA 5' 'SAIDA 2'; do
    read -r kind quantity <<<"$move"
    jq -n --arg k "$kind" --argjson q "$quantity" --argjson p "$product" \
      '{tipoMovimentacao: $k, quantidadeMovimentada: $q, produtoId: $p}' |
      post "$app/api/movimentos" "$token" >/dev/null
  done
  credentials "$user" "$password" '{identifier: $u, password: $p}' | post "$auth/api/auth/login" >/dev/null
  echo "seed-api: $user@example.com signed up, moved product $product in and out, signed in again"
done
