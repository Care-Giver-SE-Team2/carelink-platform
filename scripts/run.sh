#!/usr/bin/env bash
# Runs services locally with docker compose, from the same images the cloud runs.
#
#   scripts/run.sh <service|all>   build the image(s) with scripts/build.sh, then start them
#                                  together with what they need (see docker-compose.yml)
#   scripts/run.sh down            stop and remove the containers; the database volume stays
#
# The first run writes random local database passwords to .env, which git ignores, so no
# password is ever committed. The front end runs on its own: cd frontend && npm run dev
# (it proxies /api to http://localhost:8080).
set -euo pipefail
cd "$(dirname "$0")/.."

[ $# -eq 1 ] || {
  echo "usage: scripts/run.sh <service|all|down>" >&2
  exit 2
}

if [ "$1" = down ]; then
  docker compose down
  exit 0
fi

if [ ! -f .env ]; then
  (
    umask 077
    {
      echo "MYSQL_ROOT_PASSWORD=$(openssl rand -hex 16)"
      echo "MYSQL_PASSWORD=$(openssl rand -hex 16)"
    } >.env
  )
  echo "wrote .env with new local database passwords"
fi

IMAGE_TAG=${IMAGE_TAG:-$(git rev-parse HEAD)}
export IMAGE_TAG
scripts/build.sh "$1" image

if [ "$1" = all ]; then
  docker compose up -d --wait
else
  docker compose up -d --wait "$1"
fi
docker compose ps
