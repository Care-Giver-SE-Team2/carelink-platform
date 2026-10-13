#!/usr/bin/env bash
# Runs on the GitHub-hosted preparation job; downloads only successful main CI artifacts.
set -euo pipefail
cd "$(dirname "$0")/.."
[ $# -eq 1 ] || { echo "usage: $0 <new-release.json>" >&2; exit 2; }
: "${GH_REPO:?set GH_REPO}"
: "${IMAGE_REGISTRY:?set IMAGE_REGISTRY}"
[[ "$GH_REPO" == Care-Giver-SE-Team2/carelink-platform ]] || exit 2
records=$(mktemp -d)
trap 'rm -rf "$records"' EXIT
arguments=()
for service in core visit report notification; do
  case $service in
    core) run_id=${CORE_RUN_ID:?set CORE_RUN_ID} ;;
    visit) run_id=${VISIT_RUN_ID:?set VISIT_RUN_ID} ;;
    report) run_id=${REPORT_RUN_ID:?set REPORT_RUN_ID} ;;
    notification) run_id=${NOTIFICATION_RUN_ID:?set NOTIFICATION_RUN_ID} ;;
  esac
  [[ "$run_id" =~ ^[0-9]+$ ]] || { echo "Invalid CI run ID for $service" >&2; exit 2; }
  metadata=$(gh api "repos/$GH_REPO/actions/runs/$run_id")
  if ! jq -e '
    .status == "completed" and .conclusion == "success" and .head_branch == "main"
    and .path == ".github/workflows/ci.yml"
    and (.event == "push" or .event == "workflow_dispatch")
    and .repository.id == 1408273257 and .head_repository.id == 1408273257
  ' <<<"$metadata" >/dev/null; then
    echo "$service: select a successful main CI publication from this repository" >&2
    exit 1
  fi
  sha=$(jq -r '.head_sha' <<<"$metadata")
  [[ "$sha" =~ ^[0-9a-f]{40}$ ]] || exit 1
  gh run download "$run_id" --repo "$GH_REPO" \
    --name "ecr-reference-$service-$sha" --dir "$records/$service"
  record="$records/$service/image-record.json"
  jq -e --arg service "$service" --arg sha "$sha" \
    '.service == $service and .tag == $sha' "$record" >/dev/null
  arguments+=(--record "$record")
  echo "$service: verified publication from CI run $run_id ($sha)"
done
python3 scripts/release-manifest.py --registry "$IMAGE_REGISTRY" build "${arguments[@]}" --output "$1"
