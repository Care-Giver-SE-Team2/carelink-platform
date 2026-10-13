#!/usr/bin/env bash
# Use this entry point locally or on a runner after runtime configuration is prepared.
# AWS_PROFILE selects local credentials; omit it when the runner uses OIDC credentials.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ $# -ne 1 ]; then
  echo "usage: bash scripts/deploy-release.sh <staging|demo>" >&2
  exit 2
fi
: "${RELEASE_MANIFEST:?set RELEASE_MANIFEST}"
: "${IMAGE_REGISTRY:?set IMAGE_REGISTRY}"
: "${KUBE_CONTEXT:?set KUBE_CONTEXT}"
if [ -n "${IMAGE_TAG:-}" ] || [ "${DRY_RUN:-0}" != 0 ]; then
  echo "For offline rendering use DRY_RUN=1 scripts/deploy.sh; deploy-release.sh requires a release manifest" >&2
  exit 2
fi
check=(python3 scripts/check-deployment.py "$1" --manifest "$RELEASE_MANIFEST"
  --registry "$IMAGE_REGISTRY" --context "$KUBE_CONTEXT")
if [ -n "${AWS_PROFILE:-}" ]; then check+=(--profile "$AWS_PROFILE"); fi
"${check[@]}"
exec bash scripts/deploy.sh all "$1"
