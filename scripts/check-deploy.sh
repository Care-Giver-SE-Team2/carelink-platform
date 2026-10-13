#!/usr/bin/env bash
# Offline Helm checks for every service/environment combination. No AWS or cluster required.
set -euo pipefail
cd "$(dirname "$0")/.."

for environment in staging demo; do
  for values in services/*/deploy/values.yaml; do
    service=${values#services/}
    service=${service%%/*}
    helm lint charts/carelink-service --strict \
      -f "$values" -f "deploy/environments/$environment.yaml" \
      --set-string "image.repository=registry.invalid/carelink/$service" \
      --set-string image.tag=check \
      --set-string "envFromSecret=$service-runtime"
  done
  DRY_RUN=1 IMAGE_REGISTRY=registry.invalid IMAGE_TAG=check scripts/deploy.sh all "$environment" >/dev/null
  echo "Rendered all services for $environment"
done

# Synthetic IDs for offline rendering only; never apply these values.
helm lint charts/carelink-ingress --strict \
  --set-json 'privateSubnetIds=["subnet-00000000000000001","subnet-00000000000000002"]' \
  --set cloudFrontPrefixListId=pl-00000000000000001
PUBLIC_HTTPS=true DRY_RUN=1 IMAGE_REGISTRY=registry.invalid IMAGE_TAG=check \
  scripts/deploy.sh all staging >/dev/null
echo "Rendered the private ALB routes and public HTTPS service configuration"
