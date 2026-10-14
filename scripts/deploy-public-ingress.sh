#!/usr/bin/env bash
# Update routes while retaining the existing reviewed network bindings.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KUBE_CONTEXT:?set KUBE_CONTEXT}"
values=$(mktemp)
trap 'rm -f "$values"' EXIT
helm get values carelink-api --namespace staging --kube-context "$KUBE_CONTEXT" --output json |
  python3 -c 'import json,re,sys; v=json.load(sys.stdin); subnets=v["privateSubnetIds"]; prefix=v["cloudFrontPrefixListId"]; assert len(subnets)==2 and all(re.fullmatch(r"subnet-[0-9a-f]+",s) for s in subnets); assert re.fullmatch(r"pl-[0-9a-f]+",prefix); print(json.dumps({"privateSubnetIds":subnets,"cloudFrontPrefixListId":prefix}))' > "$values"
helm upgrade carelink-api charts/carelink-ingress --namespace staging \
  --kube-context "$KUBE_CONTEXT" --values "$values" --wait --timeout 10m
