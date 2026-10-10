#!/usr/bin/env bash
# Builds one service, or all of them, the same way: compile, test, image.
#
#   scripts/build.sh <service|all> [compile|test|image|all]
#
#   compile   compile and package the service and the shared libraries it uses (libs/), without
#             tests, and install them in the local Maven repository
#   test      unit tests, architecture tests (ArchUnit), MySQL integration tests
#             (Testcontainers, needs Docker) and the domain coverage check, for the service and
#             the shared libraries it uses
#   image     the Docker image from build/Dockerfile, tagged with the commit
#   all       the three, in that order (the default)
#
# The pipeline (.github/workflows/service.yml) calls the same stages one at a time, so a
# build on a laptop and a build in CI run the same commands.
#
# Environment:
#   IMAGE_TAG     image tag (default: the current commit)
#   IMAGE_PREFIX  image name prefix (default: carelink); the image is <prefix>/<service>:<tag>
set -euo pipefail
cd "$(dirname "$0")/.."

usage() {
  echo "usage: scripts/build.sh <service|all> [compile|test|image|all]" >&2
  exit 2
}

[ $# -ge 1 ] && [ $# -le 2 ] || usage
target=$1
stage=${2:-all}
case $stage in compile | test | image | all) ;; *) usage ;; esac

if [ "$target" = all ]; then
  services=$(for pom in services/*/pom.xml; do basename "$(dirname "$pom")"; done)
elif [ -f "services/$target/pom.xml" ]; then
  services=$target
else
  echo "no service '$target': expected services/$target/pom.xml" >&2
  exit 2
fi

prefix=${IMAGE_PREFIX:-carelink}

# The service together with the shared libraries it depends on
maven() {
  local service=$1
  shift
  ./mvnw -B -ntp -pl "services/$service" -am "$@"
}

runs() { [ "$stage" = "$1" ] || [ "$stage" = all ]; }

for service in $services; do
  if runs compile; then
    echo "==> $service: compile"
    # install, not just package: the steps that look at one service on its own (Sonar, the
    # dependency scan) find the shared libraries in the local Maven repository
    maven "$service" install -DskipTests
  fi
  if runs test; then
    echo "==> $service: test"
    maven "$service" verify -Pintegration
  fi
  if runs image; then
    commit=$(git rev-parse HEAD)
    tag=${IMAGE_TAG:-$commit}
    echo "==> $service: image $prefix/$service:$tag"
    docker build -f build/Dockerfile \
      --build-arg SERVICE="$service" \
      --build-arg GIT_SHA="$commit" \
      -t "$prefix/$service:$tag" .
  fi
done
