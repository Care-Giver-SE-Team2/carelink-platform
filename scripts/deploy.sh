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
#   KUBE_CONTEXT    explicit target cluster context (required unless DRY_RUN=1)
#   RELEASE_MANIFEST complete four-service JSON release; uses per-service tags/digests
#                    instead of IMAGE_TAG (IMAGE_REGISTRY required; paths relative to repo)
#
# Environment values come from deploy/environments/<env>.yaml. Each service reads
# <service>-runtime in the target namespace; create these Secrets before deployment.
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
  for s in $deployable; do
    case " $order " in
      (*" $s "*) ;;
      (*) echo "$s" ;;
    esac
  done
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
image_rows=""
if [ -n "${RELEASE_MANIFEST:-}" ]; then
  if [ -n "${IMAGE_TAG:-}" ]; then
    echo "Use RELEASE_MANIFEST or IMAGE_TAG, not both" >&2
    exit 2
  fi
  : "${IMAGE_REGISTRY:?set IMAGE_REGISTRY when using RELEASE_MANIFEST}"
  # Validate every record before any Kubernetes read/write or Helm release.
  image_rows=$(python3 scripts/release-manifest.py --registry "$IMAGE_REGISTRY" resolve "$RELEASE_MANIFEST")
fi
if [ "${DRY_RUN:-0}" = 1 ]; then
  registry=${IMAGE_REGISTRY:-registry.invalid}
else
  registry=${IMAGE_REGISTRY:?set IMAGE_REGISTRY to the ECR registry}
  context=${KUBE_CONTEXT:?set KUBE_CONTEXT to the intended cluster context}
  # Both cloud environment files mount this public RDS truststore.
  truststore=$(kubectl --context "$context" --namespace "$env" get configmap rds-ca \
    -o 'go-template={{index .binaryData "rds-ca.p12"}}')
  if [ -z "$truststore" ] || [ "$truststore" = '<no value>' ]; then
    echo "$env/rds-ca: missing or empty binaryData rds-ca.p12" >&2
    exit 1
  fi
  # Check every selected service before applying any release. Only key names are
  # printed by kubectl; no passwords are written to the terminal or Helm values.
  for service in $selected; do
    # shellcheck disable=SC2016 # $key and $value belong to the Go template, not Bash.
    keys=$(kubectl --context "$context" --namespace "$env" get secret "$service-runtime" \
      -o 'go-template={{range $key, $value := .data}}{{if $value}}{{$key}}{{"\n"}}{{end}}{{end}}')
    required="DB_URL DB_USER DB_PASSWORD SPRING_DATA_REDIS_HOST SPRING_DATA_REDIS_PORT SPRING_DATA_REDIS_PASSWORD SPRING_DATA_REDIS_SSL_ENABLED"
    case $service in core | visit | report) required="$required CARELINK_EVENTS_TOPICARN" ;; esac
    case $service in report | notification) required="$required CARELINK_EVENTS_QUEUEURL" ;; esac
    for key in $required; do
      if ! grep -qx "$key" <<<"$keys"; then
        echo "$env/$service-runtime: missing or empty $key" >&2
        exit 1
      fi
    done
  done
fi

for service in $selected; do
  digest=""
  if [ -n "$image_rows" ]; then
    row=$(awk -v service="$service" '$1 == service {print $2, $3}' <<<"$image_rows")
    if [ -z "$row" ]; then
      echo "Release manifest has no image for $service" >&2
      exit 1
    fi
    read -r tag digest <<<"$row"
  fi
  release=(
    "$service" charts/carelink-service
    --namespace "$env"
    -f "services/$service/deploy/values.yaml"
    -f "deploy/environments/$env.yaml"
    --set-string "envFromSecret=$service-runtime"
    --set-string "image.repository=$registry/carelink/$service"
    --set-string "image.tag=$tag"
    --set-string "image.digest=$digest"
  )
  if [ "${DRY_RUN:-0}" = 1 ]; then
    helm template "${release[@]}"
  else
    echo "==> $service -> $env ($tag)"
    # Runtime configuration bootstrap already creates the namespace. The CD role
    # only has namespace-scoped access and must not need namespaces/create.
    helm upgrade --install "${release[@]}" --kube-context "$context" --wait --timeout 10m
  fi
done
