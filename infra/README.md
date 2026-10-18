# CareLink IaC source handoff — review only

This is a source handoff, not a provisioning or teardown request. No workflow imports or runs
these Terraform roots automatically. Do not apply the examples to the running environment.

**Merging into main triggers ordinary application CI/CD when CI passes and the existing automatic
deployment flags remain enabled.** This PR does not change workflow definitions or deployment
switches, but a source-only merge is not deployment-free. CI can reuse source-identical artifacts
when a verified baseline is available. CodeBuild still executes the deployment/Ingress checks,
and frontend publication uploads artifacts and a new version marker and invalidates CloudFront.
Unchanged backend manifests/images can skip the service upgrade; this does not skip all online work.
These steps keep the existing distribution/domain; they do not run Terraform apply/destroy.
If no deployment is allowed at this stage, keep this PR unmerged. Do not disable normal CD silently.

## What is included

| Terraform root | Owns | Does not include |
| --- | --- | --- |
| `infra/bootstrap` | Private, encrypted, versioned state bucket, with `prevent_destroy` | Existing backend/state recovery or account administration |
| `infra` | Four immutable ECR repositories, GitHub OIDC publisher role | EKS application deployment or CodeBuild executor |
| `infra/runtime` | VPC, NAT, EKS, node groups, Pod Identity Agent, optional RDS/Redis/Secrets and SNS/SQS, LB Controller identity | Helm installation of metrics-server/KEDA/LB Controller, business workloads, CodeBuild project/access entries |
| `infra/public-site` | Private static S3, CloudFront/VPC origin, optional WAF, frontend publication role | Controller-created ALB, security-header policy creation, custom domain/certificate |
| `infra/report-worker` | Dedicated report task SQS/DLQ, SNS subscription, bounded Worker/KEDA roles and Pod Identity associations, separate state key | Existing runtime/data/network changes, administrator boundary creation, KEDA Helm installation, business fixtures |

The report-worker root is a new, opt-in integration increment; its source is not part of the earlier
local-source hash manifest. See `infra/report-worker/README.md` for its IAM and first-plan gates.

The files were allowlisted from the operator's local working copy, which had not been committed.
`source-manifest.json` records original source hashes and handed-off hashes. No local state,
plan, provider cache, real tfvars/backend inputs, passwords, SQL dumps, kubeconfigs, IAM
session files, cleanup timers or historical `.local` orchestration scripts are included.
The public-site hardcoded live response-header policy ID was replaced with a **required input**.
Its supplied ID must already exist and be reviewed in the selected account. Repository-specific
OIDC subject IDs are retained deliberately; they are not credentials and must be reviewed for a fork.

## Safe validation

From the repository root, install the recorded Terraform version (verified with 1.16.5), then run:

```sh
python3 scripts/validate-iac-offline.py
```

The validator copies only Terraform source, tests, lockfiles, controller policy and the public SPA
function into a disposable directory. It removes inherited AWS/TF environment overrides, does not
copy real input/state files, initializes with `-backend=false` and runs only mocked-provider tests.
A test's `command = apply` uses Terraform's mock provider, not an AWS apply. Provider download
requires network access. The original four roots pin AWS 6.68.0; the new Worker root pins 6.69.1.
All five roots must pass independently.
These checks prove configuration/test wiring, not live IAM authorization, drift or a safe real plan.
Checkov policy assessment is still a separate workstream-five handoff; this is not a Checkov pass.

## Development defaults are not live HA settings

Runtime defaults deliberately retain the original development path: HA false, data/messaging/ingress
opt-ins false, `ha_worker_migration="legacy"`. Existing live HA previously used
`high_availability=true` and migration `complete`, with data/messaging/ingress enabled.
**Do not connect an example configuration to an existing backend and plan/apply it.** Those defaults
could propose removal of the read replica, Redis redundancy, second NAT, HA nodes, Secrets or roles.
Recover the authoritative current inputs, state keys, snapshot source and run ID privately first.
Changing `data_run_id`, snapshot inputs, credential versions or migration stages is not a harmless
example update. A saved plan must explicitly prove no unintended database replacement or deletion.
This PR produces no live plan and makes no assertion that the source is drift-free.

There are separate state keys for persistent platform, runtime and public-site. Their backend examples
use fictional account IDs; bootstrap has local state initially. Never initialize another root against
the wrong key, migrate existing state automatically, or create duplicate fixed-name resources.
The scripts which generated credentials or applied historical SQL are intentionally excluded.
Existing credentials must be recovered through the authorized secret process, not regenerated.
Terraform write-only ephemeral inputs keep secrets out of those resource attributes, but state/plan
files generally remain sensitive and must never be committed.

## Handoff and live-readiness documents

- [Dependency order, ownership and remaining acceptance](../docs/platform/iac-handoff.md)
- [Read-only budgets, metrics-server and KEDA findings](../docs/platform/platform-readiness-20261018.md)
- [Runtime input and lifecycle notes](runtime/README.md)

This handoff does not meet “one command rebuilds the complete environment” yet: admin boundaries,
controller/component installs, CodeBuild execution identity/network/access, database initialization and
full new-environment acceptance remain explicit dependencies. It also does not offer an automatic
destroy command. Runtime teardown would leave state bucket, ECR/OIDC/IAM and required snapshots
according to the agreed retention policy; it must first remove public/Ingress/ALB dependencies.
There is no scheduled cleanup in this PR.
