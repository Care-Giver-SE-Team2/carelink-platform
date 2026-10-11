#!/usr/bin/env bash
# Makes sure a service's SonarCloud project exists, is public, and has "main" as its main branch.
# The pipeline runs it before every analysis (.github/workflows/service.yml, step 3).
#
# Why: a project that the first analysis creates is private, with "master" as its main branch, and
# the free plan analyses only the main branch of a private project. The first push to main after
# a new service's first pull request would then fail at the quality gate with "Organization is not
# allowed to access data from non main branches". The repository is public, so its analyses are too.
#
# The first analysis of a new project's main branch has nothing to compare with, so SonarCloud gives it
# no quality gate (NONE), and the scanner's wait for the gate fails once. Re-run the job: the second
# analysis has a gate.
#
#   SONAR_TOKEN   a token of an organization admin (the pipeline's secret)
#   SONAR_ORG     the organization key
#   PROJECT       the project key, <prefix>_<service>
#   NAME          the project's display name, the service's name
set -euo pipefail

api=https://sonarcloud.io/api
auth=(-H "Authorization: Bearer $SONAR_TOKEN")

found=$(curl -fsS "${auth[@]}" "$api/projects/search?organization=$SONAR_ORG&projects=$PROJECT" | jq '.components | length')
if [ "$found" = 0 ]; then
  curl -fsS "${auth[@]}" -X POST "$api/projects/create" \
    --data-urlencode "organization=$SONAR_ORG" --data-urlencode "project=$PROJECT" \
    --data-urlencode "name=$NAME" --data-urlencode "visibility=public" >/dev/null
  echo "created $PROJECT, public"
fi

curl -fsS "${auth[@]}" -X POST "$api/projects/update_visibility" \
  --data-urlencode "project=$PROJECT" --data-urlencode "visibility=public" >/dev/null

branches=$(curl -fsS "${auth[@]}" "$api/project_branches/list?project=$PROJECT")
main=$(jq -r '.branches[] | select(.isMain) | .name' <<<"$branches")
if [ "$main" != main ]; then
  # An analysis of main that was taken as another branch would stand in the rename's way
  if jq -e '.branches[] | select(.isMain | not) | select(.name == "main")' <<<"$branches" >/dev/null; then
    curl -fsS "${auth[@]}" -X POST "$api/project_branches/delete" \
      --data-urlencode "project=$PROJECT" --data-urlencode "branch=main" >/dev/null
  fi
  curl -fsS "${auth[@]}" -X POST "$api/project_branches/rename" \
    --data-urlencode "project=$PROJECT" --data-urlencode "name=main" >/dev/null
  echo "$PROJECT's main branch renamed from $main to main"
fi
echo "$PROJECT is public, main branch main"
