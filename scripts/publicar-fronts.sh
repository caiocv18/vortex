#!/bin/bash
# Builds the single-page apps from the committed HEAD and publishes them to the static
# site stack, infra/terraform/site (ADR 0007).
#
# Usage: AWS_PROFILE=<profile> scripts/publicar-fronts.sh [app] [login]
#   With no argument both sites are published, one after the other. app is the
#   inventory app at the apex domain; login is the login app on its subdomain.
#
# Needs git, Node.js with npm (26 as in CI; 24.x from 24.15 and 22.x from 22.22.2 also
# meet the fronts' dependencies, 23 and 25 don't), the AWS CLI v2 and Terraform, and the
# site stack initialized with its backend:
#   terraform -chdir=infra/terraform/site init -backend-config=../backend.hcl
#
# The build runs on a git archive export of HEAD in a temporary directory, so local
# .env and .env.local files and uncommitted changes never reach the published site.
# The URLs each app calls are written into the build, taken from the stack's outputs.
set -euo pipefail
# Without this, bash turns errexit off inside $(...), and build_site runs in one.
shopt -s inherit_errexit

die() {
  printf 'publicar-fronts: %s\n' "$*" >&2
  exit 1
}

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(git -C "$script_dir" rev-parse --show-toplevel)
site_dir="$repo_root/infra/terraform/site"

sites=("$@")
if [[ ${#sites[@]} -eq 0 ]]; then
  sites=(app login)
fi
for site in "${sites[@]}"; do
  case "$site" in
    app | login) ;;
    *) die "unknown site '$site'; use app, login, or no argument for both" ;;
  esac
done

for tool in git node npm aws terraform tar; do
  command -v "$tool" >/dev/null || die "$tool is not in PATH"
done
node_version=$(node --version)
# The strictest engines among the fronts' dependencies: ^22.22.2 || ^24.15.0 || >=26.
node -e 'const [a, b, c] = process.versions.node.split(".").map(Number); process.exit(a >= 26 || (a === 24 && b >= 15) || (a === 22 && (b > 22 || (b === 22 && c >= 2))) ? 0 : 1)' ||
  die "Node.js $node_version does not meet the fronts' dependencies; use 26 (as in CI), 24.x from 24.15, or 22.x from 22.22.2"
if [[ -z ${AWS_PROFILE:-} && -z ${AWS_ACCESS_KEY_ID:-} ]]; then
  die "set AWS_PROFILE, or credentials in the environment"
fi
aws sts get-caller-identity --query Arn --output text >/dev/null ||
  die "the AWS credentials do not work; run aws login --profile \"\$AWS_PROFILE\""

tf_output() {
  local name=$1
  terraform -chdir="$site_dir" output -raw "$name" ||
    die "no output $name; is infra/terraform/site applied and initialized with its backend?"
}

app_url=$(tf_output app_url)
login_url=$(tf_output login_url)

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# Resolved once, so both sites come from the same commit even if HEAD moves mid-run.
commit=$(git -C "$repo_root" rev-parse --verify 'HEAD^{commit}')
sha=$(git -C "$repo_root" rev-parse --short "$commit")
frontend_changes=$(git -C "$repo_root" status --porcelain -- frontend)
if [[ -n $frontend_changes ]]; then
  printf 'warning: frontend/ has uncommitted changes; only commit %s is published\n' "$sha" >&2
fi
printf 'publishing %s from commit %s\n' "${sites[*]}" "$sha"

# Prints the dist directory of the site it built.
build_site() {
  local site=$1 front src
  case "$site" in
    app) front=vortex-application-service ;;
    login) front=vortex-authorization-service ;;
    *) die "unknown site '$site'" ;;
  esac
  src="$work/frontend/$front"
  git -C "$repo_root" archive --format=tar "$commit" "frontend/$front" | tar -x -C "$work"

  printf 'building %s (frontend/%s)\n' "$site" "$front" >&2
  (
    cd "$src"
    # Vite reads VITE_* from the environment over any file, and NODE_ENV changes both
    # what npm ci installs and what Vite builds: only the values below may count.
    unset NODE_ENV "${!VITE_@}"
    npm ci --ignore-scripts --no-audit --no-fund >&2
    case "$site" in
      app)
        # The generated API client's paths have no /api, so the base URL carries it.
        # The auth URLs are origins: the code appends /api/auth/... itself.
        VITE_API_BASE_URL="$app_url/api" \
          VITE_AUTH_BACKEND_URL="$app_url" \
          VITE_AUTH_SERVICE_URL="$login_url" \
          npm run build-only >&2
        ;;
      login)
        # VITE_MAIN_APP_URL is also the only origin accepted as returnUrl after login.
        VITE_API_URL="$login_url" \
          VITE_MAIN_APP_URL="$app_url" \
          npm run build >&2
        ;;
      *) die "unknown site '$site'" ;;
    esac
  )
  [[ -f $src/dist/index.html ]] || die "the $site build left no dist/index.html"
  printf '%s\n' "$src/dist"
}

publish_site() {
  local site=$1 dist=$2 bucket distribution url_var="${1}_url"
  bucket=$(tf_output "${site}_bucket")
  distribution=$(tf_output "${site}_distribution_id")

  # Hashed assets first, so the new index.html never points at a file that is not
  # uploaded yet. Old assets stay for visitors who still have the previous index.html.
  if [[ -d $dist/assets ]]; then
    aws s3 sync "$dist/assets" "s3://$bucket/assets" \
      --cache-control 'public, max-age=31536000, immutable' \
      --exclude '.*' --exclude '*/.*' --only-show-errors
  fi
  # The unhashed files keep their size from one build to the next, so sync would
  # decide by modification time alone and skip them whenever the local clock lags
  # behind S3's. They are always copied instead, index.html last, and revalidated on
  # every visit.
  aws s3 cp "$dist" "s3://$bucket" --recursive \
    --cache-control 'no-cache' \
    --exclude 'assets/*' --exclude 'index.html' --exclude '.*' --exclude '*/.*' --only-show-errors
  aws s3 cp "$dist/index.html" "s3://$bucket/index.html" \
    --cache-control 'no-cache' --only-show-errors
  # Removes what the new build no longer has. --delete leaves the excluded paths
  # alone, so it never removes an old asset; a file it uploads again keeps no-cache.
  aws s3 sync "$dist" "s3://$bucket" --delete \
    --cache-control 'no-cache' \
    --exclude 'assets/*' --exclude '.*' --exclude '*/.*' --only-show-errors

  # The viewer-request function maps every route to /index.html before the cache
  # lookup, so this one path covers all of them.
  aws cloudfront create-invalidation --distribution-id "$distribution" \
    --paths '/index.html' --query 'Invalidation.Id' --output text >/dev/null
  printf '%s published: %s\n' "$site" "${!url_var}"
}

for site in "${sites[@]}"; do
  dist=$(build_site "$site")
  publish_site "$site" "$dist"
done
