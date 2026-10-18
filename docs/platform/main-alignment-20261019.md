# Local integration of caregiver migration with main (2026-10-19)

The caregiver migration at `b486ccd` and the Redis fix at `1d3c3d8` are retained in history.
The migration branch integrates main `59a91a3c021d0d85a76faf1dd64690c4b4d5c99e` through a local merge.
All 30 paths introduced or changed by that main delta are retained unchanged.
This record covers local compatibility, not deployment or formal 6b capacity acceptance.

## Report migration resolution

- Keep main's `V4__report_task_dispatch.sql` unchanged, including already-applied checksums.
- Rename this feature's unpublished evidence migration from `V4__visit_evidence_projection.sql`
  to `V5__visit_evidence_projection.sql`; the SQL body is identical.
- Real MySQL tests cover V3 -> V5 and main V4 -> V5, preserving legacy counters, dispatch claims
  and the existing V4 history. Fresh service initialization also succeeds with both tables.
- Use a clean build directory to exclude stale copies of the former V4 resource.
- The earlier feature-only V4 was tested in disposable local databases and was not deployed by
  this task. It is a different history from main V4: recreating owned disposable fixtures is
  appropriate; shared databases require an explicit migration plan. Do not run Flyway repair,
  change an applied checksum or edit shared history to make this rename pass validation.

## Scope beyond the merge

Only the evidence migration filename, two report integration test classes and these handoff
documents change beyond the existing branch and main. Caregiver application code, private
storage, service boundaries, shared-session implementation and main's worker/deployment
configuration remain unchanged. The tests cover producer idempotency, worker preparation,
transaction rollback/retry, evidence snapshots and repeated tasks together.

## Completed local verification

| Check | Result |
| --- | --- |
| Backend unit tests | 2021 passed, zero failures/errors/skips |
| Table-ownership checks | 5 passed, included in the unit count |
| Selected integrations | 160 passed, zero failures/errors/skips |
| New report upgrade / worker-evidence tests | 2 / 2 passed, included in integrations |
| Production packages | All four service JARs built offline |
| Real frontend and business flow | 16 checkpoints passed on the combined JARs |
| Real report API + worker profiles | 5 checks passed using isolated LocalStack SNS/SQS |
| Incremental database upgrades | core26 ->28, visit2 ->5, main report4 ->5; existing facts preserved |
| Report backup / restore | Dispatch claims, reports, basis, snapshots and V4/V5 checksums preserved; Flyway validate and zero-change migrate pass |
| Cleanup | Owned temporary resources removed; five pre-existing stopped containers unchanged |

The real business run repeats registration, independent certificate/admission review, scheduling,
check-in/task/health/private evidence/check-out, family reads, own history and immutable alerts.
One family cookie works across all four services. A separate synthetic historical report fixture
drives the production task producer -> outbox -> filtered local SNS/SQS -> worker -> three audience
reports, using real core HTTP and rule generation. Duplicate envelopes and repeated logical tasks
leave one dispatch claim, one basis and three reports. Private attachment payloads are not sent
through these report events.

The integration tests use service-local MySQL with the existing core stand-in; the new worker
compatibility test stubs CoreApi and seeds the real KeptFacts store, because the worker intentionally
excludes API fact handlers. Its first two fixture errors were corrected without changing production
code; the corrected class and the previously skipped notification class were re-run. Other passing
classes retain their first-run provenance; the integration total counts each final class once.
The real runtime check uses real core HTTP and separate API/worker consumers. External validation
hooks only trigger existing production operations and remain outside application sources/JARs.
No real model, ImageKit, cloud queue, Kubernetes, HPA/KEDA or production data was used.

The first Windows PowerShell 5 runner stopped on a Mockito stderr warning. It is retained as a
harness interruption, not an application failure or a passing full run. The successful complete
unit rerun used PowerShell 7. Frontend source/dependencies did not change in this merge; the earlier
1300-test frontend result is historical and was not re-run. The real 16-checkpoint browser flow was.
The isolated dependency junction initially caused font asset403 responses; the external Vite launcher
now allows that exact dependency realpath, and four font assets were independently checked for200
responses and matching bytes. Application frontend code did not change for this harness correction.
The business flow and its first four worker checks passed in the first runtime environment. Its
restore probe stopped on a host/client character-set decoding error and the environment was cleaned.
A fresh smaller core/API/worker run used identical application JAR hashes, explicitly selected
utf8mb4 for MySQL reads/dumps, and passed all five worker/restore checks and the upgrade proofs.
Both run manifests and the original interruption are retained; these are separate runs, not a
claim that the entire first runtime attempt passed. No application fix was needed for this probe.

## Remaining work

The original 6b late-logout cookie, cross-tab body/sidebar, real hidden-tab recovery,
elder/deep-link consistency and precise visit in-flight interleaving remain open. This merge
does not close them or produce new formal cache/HPA/performance results. Boards2/4 still need
to confirm storage, resource budgets, queues, release order and the authorized test window.
The main KEDA configuration files are inherited unchanged; no deployment script was executed.
