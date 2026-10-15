#!/usr/bin/env bash
# No AWS credentials: select only an artifact from a successful main CI run.
set -euo pipefail
cd "$(dirname "$0")/.."
[ $# -eq 1 ] || exit 2
: "${GH_REPO:?set GH_REPO}"
: "${FRONTEND_RUN_ID:?set FRONTEND_RUN_ID}"
[[ "$GH_REPO" == Care-Giver-SE-Team2/carelink-platform ]]
[[ "$FRONTEND_RUN_ID" =~ ^[0-9]+$ ]]
metadata=$(gh api "repos/$GH_REPO/actions/runs/$FRONTEND_RUN_ID")
jq -e '
  .status == "completed" and .conclusion == "success" and .head_branch == "main"
  and .path == ".github/workflows/ci.yml"
  and (.event == "push" or .event == "workflow_dispatch")
  and .repository.id == 1408273257 and .head_repository.id == 1408273257
' <<<"$metadata" >/dev/null
sha=$(jq -r '.head_sha' <<<"$metadata")
[[ "$sha" =~ ^[0-9a-f]{40}$ ]]
if [ -n "${RELEASE_SHA:-}" ] && [ "$sha" != "$RELEASE_SHA" ]; then
  echo "Frontend CI commit differs from the selected release" >&2
  exit 1
fi
artifacts=$(gh api "repos/$GH_REPO/actions/runs/$FRONTEND_RUN_ID/artifacts?per_page=100")
if jq -e --arg name "verified-release-$sha" '.artifacts[] | select(.name == $name and .expired == false)' <<<"$artifacts" >/dev/null; then
  bundle=$(mktemp -d)
  trap 'rm -rf "$bundle"' EXIT
  gh run download "$FRONTEND_RUN_ID" --repo "$GH_REPO" --name "verified-release-$sha" --dir "$bundle"
  mkdir -p "$1"
  cp -R "$bundle/frontend/." "$1/"
else
  # Existing successful releases remain usable for explicit manual rollback.
  gh run download "$FRONTEND_RUN_ID" --repo "$GH_REPO" --name "frontend-dist-$sha" --dir "$1"
fi
python3 scripts/frontend-release.py verify --directory "$1" --sha "$sha"
