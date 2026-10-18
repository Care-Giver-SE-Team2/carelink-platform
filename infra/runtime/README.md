# Runtime source boundary

See [the handoff entry](../README.md) before using this Terraform root. This is a reviewed source
snapshot, not the current account's inputs or a deployment command.

- `network.tf`: explicit AZs, public/app/data subnets, zonal NAT and S3 gateway endpoint.
- `eks.tf`: cluster, explicit operator access, launch template, legacy/HA node groups and Pod Identity Agent.
- `data.tf`, `data-secrets.tf`: opt-in private MySQL/Redis, backups and write-only secret payloads.
- `messaging.tf`: opt-in SNS, four SQS/DLQ pairs and workload identities. Only report and notification
  have subscriptions; a queue's existence does not prove application consumption.
- `load-balancer-controller.tf`: controller IAM identity only. Controller Helm installation is separate.
- `controller-policy.json`: account-templated controller policy; review it with the required external boundary.

`high_availability` affects NAT, RDS standby/reader and Redis replica. Node size is controlled separately
by the staged `ha_worker_migration` setting: legacy → expand → complete. The source supports both
paths; the running HA environment must not be reconciled using the development defaults.
`complete` removes the legacy group definitions, so change stages only after deliberate node migration.
Both per-AZ groups cap desired/min/max at one; this is not automatic peak-capacity node scaling.

RDS has `prevent_destroy`, deletion protection and required final snapshot configuration. These guards
are not a general proof of plan safety. Other counted/conditional resources can still be removed if
wrong flags are supplied. Keep current snapshot/run IDs and credential versions unchanged until a
specific migration plan is reviewed. Do not run `terraform destroy` to stop this environment.

External admin prerequisites include CareLinkRuntimeWorkloadBoundary,
CareLinkLoadBalancerBoundary and provisioning/PassRole/state permissions. Their existence and the
complete administrator bootstrap are not recreated by this root. Read-only runtime audit requires
cluster GET access; it does not grant itself access or alter EKS public CIDRs.

The six-value `data_credentials` input is sensitive/ephemeral; no real values are included. Application
schemas/users, RDS CA truststore and runtime Secret synchronization are separate bootstrap steps;
existing `scripts/sync-runtime-config.py` handles application delivery after those dependencies exist.
Review those existing operational scripts with the business/database owner before a fresh bootstrap.
Metrics-server and KEDA are not Terraform resources in this source snapshot.
