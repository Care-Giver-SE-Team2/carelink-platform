#!/usr/bin/env bash
# Publishes an already-built service image using local or OIDC credentials.
# Usage: AWS_PROFILE=carelink bash scripts/push-image.sh core
set -euo pipefail
cd "$(dirname "$0")/.."

if [ $# -ne 1 ]; then
  echo "usage: scripts/push-image.sh <core|visit|report|notification> (set AWS_PROFILE locally or IMAGE_REGISTRY in CI)" >&2
  exit 2
fi
service=$1
case $service in core | visit | report | notification) ;; *) exit 2 ;; esac
tag=${IMAGE_TAG:-$(git rev-parse HEAD)}
if [ -n "${IMAGE_REGISTRY:-}" ]; then
  registry=$IMAGE_REGISTRY
else
  : "${AWS_PROFILE:?Set AWS_PROFILE for local publication, or IMAGE_REGISTRY for CI.}"
  registry=$(terraform -chdir=infra output -raw image_registry)
fi
account=$(aws sts get-caller-identity --query Account --output text)
if [ "$registry" != "$account.dkr.ecr.ap-southeast-1.amazonaws.com" ]; then
  echo "AWS identity does not match the selected registry in Singapore." >&2
  exit 1
fi
source_image="carelink/$service:$tag"
target_image="$registry/carelink/$service:$tag"
source_id=$(docker image inspect "$source_image" --format '{{.Id}}')

# Keep the temporary ECR token separate from the user's Docker credentials.
auth_dir=$(mktemp -d)
cleanup() {
  docker --config "$auth_dir" logout "$registry" >/dev/null 2>&1 || true
  rm -rf "$auth_dir"
}
trap cleanup EXIT
printf '{"auths":{"%s":{}}}\n' "$registry" > "$auth_dir/config.json"
aws ecr get-login-password --region ap-southeast-1 \
  | docker --config "$auth_dir" login --username AWS --password-stdin "$registry"

# Reuse an immutable tag only if it contains the exact image we were given.
existing=$(aws ecr batch-get-image --region ap-southeast-1 \
  --repository-name "carelink/$service" --image-ids "imageTag=$tag")
if [ "$(jq '.images | length' <<<"$existing")" -gt 0 ]; then
  docker --config "$auth_dir" pull "$target_image"
  remote_id=$(docker image inspect "$target_image" --format '{{.Id}}')
  if [ "$source_id" != "$remote_id" ]; then
    echo "Immutable tag $tag already contains a different image. Use a new commit; do not overwrite it." >&2
    exit 1
  fi
  echo "Reusing the identical image already published at $target_image"
elif jq -e '.failures | length == 1 and .[0].failureCode == "ImageNotFound"' <<<"$existing" >/dev/null; then
  docker tag "$source_image" "$target_image"
  docker --config "$auth_dir" push "$target_image"
else
  echo "ECR could not establish whether the tag exists." >&2
  exit 1
fi

digest=$(aws ecr describe-images --region ap-southeast-1 \
  --repository-name "carelink/$service" --image-ids "imageTag=$tag" \
  --query 'imageDetails[0].imageDigest' --output text)
[[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]
echo "Published image: $target_image@$digest"
if [ -n "${IMAGE_RECORD:-}" ]; then
  jq -n --arg service "$service" --arg tag "$tag" --arg image "$registry/carelink/$service" \
    --arg digest "$digest" '{service: $service, tag: $tag, image: $image, digest: $digest}' > "$IMAGE_RECORD"
fi
