#!/usr/bin/env bash
# Deploys one service, or all of them, to an environment with the shared Helm chart.
#
#   scripts/deploy.sh <service|all> <staging|demo>
#
# Each service is a Helm release named after it, in the namespace named after the
# environment, installed from charts/carelink-service with the service's own
# services/<name>/deploy/values.yaml. "all" deploys core first, because the other services
# call it. Each release is a rolling update; the script waits until the new pods are ready.
#
# Environment:
#   IMAGE_REGISTRY  the ECR registry, <account>.dkr.ecr.<region>.amazonaws.com (required)
#   IMAGE_TAG       the commit to deploy (default: the current commit). staging and demo
#                   deploy the same image, built once by the pipeline
#   DRY_RUN=1       print the manifests instead of applying them; needs only helm
#
# Needs helm, and kubectl pointed at the cluster (aws eks update-kubeconfig ...).
set -euo pipefail
cd "$(dirname "$0")/.."

[ $# -eq 2 ] || {
  echo "usage: scripts/deploy.sh <service|all> <staging|demo>" >&2
  exit 2
}
target=$1
env=$2
case $env in staging | demo) ;; *)
  echo "environment must be staging or demo" >&2
  exit 2
  ;;
esac

# Every service with a values file, core first, then the services that call it
order="core visit report notification"
deployable=$(for v in services/*/deploy/values.yaml; do
  s=${v#services/}
  echo "${s%%/*}"
done)
ordered=$(
  for s in $order; do echo "$deployable" | grep -qx "$s" && echo "$s"; done
  for s in $deployable; do case " $order " in *" $s "*) ;; *) echo "$s" ;; esac; done
)

if [ "$target" = all ]; then
  selected=$ordered
elif echo "$deployable" | grep -qx "$target"; then
  selected=$target
else
  echo "no service '$target': expected services/$target/deploy/values.yaml" >&2
  exit 2
fi

tag=${IMAGE_TAG:-$(git rev-parse HEAD)}
if [ -n "${DRY_RUN:-}" ]; then
  registry=${IMAGE_REGISTRY:-registry.invalid}
else
  registry=${IMAGE_REGISTRY:?set IMAGE_REGISTRY to the ECR registry}
fi

for service in $selected; do
  release=(
    "$service" charts/carelink-service
    --namespace "$env"
    -f "services/$service/deploy/values.yaml"
    --set "image.repository=$registry/carelink/$service"
    --set "image.tag=$tag"
  )
  if [ -n "${DRY_RUN:-}" ]; then
    helm template "${release[@]}"
  else
    echo "==> $service -> $env ($tag)"
    helm upgrade --install "${release[@]}" --create-namespace --wait --timeout 10m
  fi
done
