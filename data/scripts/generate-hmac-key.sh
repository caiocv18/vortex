#!/usr/bin/env bash
# Writes a new 32-byte HMAC key to data/.env (git-ignored, mode 600) and never prints it.
# Refuses to overwrite an existing key: a new key changes every pseudonym, so rotating is a
# decision, not a side effect. To rotate, move the old file away first and keep it while any
# table still holds pseudonyms of its version.
set -euo pipefail

data_dir=$(cd "$(dirname "$0")/.." && pwd)
env_file="$data_dir/.env"

if [[ -e "$env_file" ]]; then
  echo "generate-hmac-key: $env_file already exists; refusing to replace the key" >&2
  exit 1
fi

umask 077
version="local-$(date -u +%Y%m%d)"
key=$(python3 -c 'import secrets; print(secrets.token_hex(32))')
printf 'VORTEX_HMAC_KEY=%s\nVORTEX_HMAC_KEY_VERSION=%s\n' "$key" "$version" > "$env_file"
unset key
echo "generate-hmac-key: wrote $env_file (version $version)"
