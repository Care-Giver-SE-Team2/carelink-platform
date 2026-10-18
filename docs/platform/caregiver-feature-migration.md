# Caregiver feature port (local, 2026-10-19)

This is the selected caregiver scope from upstream #98/#99 plus the supporting #100/#101/#111 changes, read at local commit `facae96eae25d20d0a4e1d37cdb888239d640a16`. It builds on platform main `20e456a8`, the preserved Redis fix `1d3c3d8`, and the prior admission port. It is now integrated locally with main `59a91a3`; deployment remains outside this record. See [combined regression](main-alignment-20261019.md). The full upstream-sync marker stays `f9bc4f1`.

## Scope and ownership

| Capability | Owning service | Contract / limit |
| --- | --- | --- |
| Registration, application, admission, contact details | core | Admission and certificate approval remain independent. Existing AVAILABLE/BUSY accounts are retained. |
| Private certificate submission, renewal, rejected resubmission, manager document reads | core | Immutable files and request receipts; JPEG/PNG/PDF; no public URL or family access to private documents. |
| Availability and work preferences | core (rostering module) | Advisory only: `NOT_APPLIED_TO_ROSTERING`; no automatic cancellation, reassignment, leave request or scoring change. |
| Own missed-check-in summaries | visit | Entitlement uses the caregiver captured by the trigger, never the elder's latest assignment. |
| PHOTO / SIGNATURE and own history | visit | Receipt actor defines own execution history. Current work-pack entitlement and own historical file entitlement are separate. |
| Own spot-check facts used in history | core, read through CoreApi | `GET /internal/v1/caregivers/by-username/{username}/own-spot-checks`; core applies its existing actor permissions. Failure is not an empty successful history. |
| Evidence count snapshots | report | `VisitEvidenceUpdated` -> independent `report_visit_evidence`; no private document bytes/references in events. |
| Notifications | notification | Consumes existing `NotificationRequested`; visit emits the caregiver-safe SYS03 request, core retains manager notification ownership. |

Public route ownership: credentials, admission and work settings go to core; `/api/caregivers/me/history`, `/api/caregivers/me/system-alerts` and visit evidence routes go to visit. Both Vite and ingress include the new visit routes. Browser authentication, shared Redis sessions and CSRF remain the platform implementation. Multipart requests omit the JSON Content-Type header so the browser supplies the boundary.

## Additive schema changes

| Service | Migration | Adds |
| --- | --- | --- |
| core | V26 (prior port) | Admission application, history and receipts |
| core | V27 | Credential guard, private document metadata, submission receipts |
| core | V28 | Work-settings version and request receipts |
| visit | V3 | Caregiver/time trigger index |
| visit | V4 | Private evidence metadata, visit/evidence and uploader indexes |
| visit | V5 | Own-history actor/month index (upstream V31) |
| report | V4 (main) | Existing report task dispatch claims, unchanged |
| report | V5 | Independent evidence snapshot projection (renamed from the feature-only V4) |

The existing visit V2 request migration is unchanged. Existing legacy credential rows without attachments and legacy evidence rows without upload ownership remain compatible; the migration does not invent old actors or private documents. Each service owns its new tables; cross-service identities are logical IDs, without cross-database foreign keys. Upgrade proofs use real Flyway: core26->28, visit2->4, report3->4, preserving synthetic legacy rows, receipts and counters. The final visitV5 index and isolated test-output path were subsequently validated after Docker recovery: fresh initialization, visit2->5 upgrade and restore preserve the actor/month index and legacy facts.

## Files, limits and local runtime

The default private paths are `/var/lib/carelink/private/credentials` (core) and `/var/lib/carelink/private/evidences` (visit). Override with `carelink.caregiver.credential-storage` and `carelink.caregiver.evidence-storage`. The runtime image creates those directories for UID1001; local Compose mounts independent named volumes. Back up the matching database metadata and immutable files together. Do not remove the named volumes to roll back a UI change.

Multipart data and bounded PDF validation use `${java.io.tmpdir}/carelink-multipart` (override `CARELINK_MULTIPART_LOCATION`), separate from durable private storage. Core limits are 5MiB/file and 16MiB/request; visit 5MiB/file and 6MiB/request. Images are decoded/re-encoded, limited to20MP, and downloads are private/no-store. PDFBox3.0.8 runs in a child JVM with a96MiB heap, two concurrent slots and a5s timeout; PDFs are restricted to20 pages without encryption/actions/attachments. Process capacity and edge limits need deployment review.

The shared Helm chart accepts `privateStorage.existingClaim` and `privateStorage.mountPath` (default `/var/lib/carelink/private`). The claim name defaults empty: this change creates no PVC and enables no cloud storage. Board2 must provision/review a writable, durable volume for UID1001, including a genuine shared read/write mode for multiple replicas. Pod-local writable layers or emptyDir are not durable attachment storage. ImageKit is an optional, disabled certificate-only adapter and does not provide visit evidence storage; no live ImageKit calls were made.

The service-code list for work preferences defaults empty. Board owners must confirm and configure `carelink.caregiver.work-settings.service-codes`; synthetic local validation uses PERSONAL_CARE only. An empty catalogue is shown honestly, not populated with unrelated task/report IDs.

## SYS03 and report consistency

visit saves the trigger ledger, publishes a Spring trigger event, and creates caregiver-safe `NotificationRequested` outbox rows before the visit transaction commits. It resolves the locked triggering caregiver through CoreApi, suppressing an extra caregiver copy for a manager account. core's incident adapter sends manager SYS03 requests and no longer guesses a caregiver from a latest elder assignment. The caregiver resource type is `CAREGIVER_SYSTEM_ALERT`; the payload omits elder identity, address, incident narrative and manager timeline. Reassignment preserves the original caregiver's minimal alert but removes current-work-pack access; valid late check-in remains allowed before reassignment.

Evidence uploads keep evidence UNVERIFIED and emit count snapshots in the same visit transaction. report orders these by visit version in its own projection. Evidence arriving before scheduling does not fabricate a visit; later state events cannot reset the counts. Until a snapshot exists, report reads legacy counters from report_visit. There is no new verifier workflow and no real LLM/Bedrock acceptance claim.

## Local verification and remaining handoff

Completed local tests (before the Docker engine fault) cover credential validation/privacy/replay/rollback, admission restrictions, work-setting versions, evidence count/version conflicts, SYS03 immutable recipients, report duplicate/out-of-order delivery, Redis sessions, and service table ownership. Real core/visit/report/notification, two extra core/visit instances, Redis, MySQL and LocalStack SNS/SQS exercise registration -> independent reviews -> real scheduling -> check-in/task/health/evidence/check-out -> family reads/history/alerts. Paired metadata/file restore and instance restart use independent restored schemas and SHA256-checked files.

Release gates remain outside this local port: Board2 storage and edge/process budgets, capacity and runtime values; Board4 event/worker compatibility and queue filter confirmation; agreed test window and rollback/backups. During the local validation phase, no repository fetch or push, deploy, cloud test, HPA/KEDA/Active-Standby activation, production data copy or live notification was performed. Repository publication is a separate delivery step and does not constitute deployment acceptance.

The existing6b failures remain separate: late logout response clearing a new account cookie, cross-tab old body/sidebar data, real hidden-tab recovery, and elder selector/deep-link consistency. This feature port does not mark those fixed. Formal cache/HPA results require a new frozen-version experiment after those correctness limits are addressed. FullCG06/offboarding/export, automatic roster consumption of preferences, manager preference editing and a new evidence verifier are outside the selected source scope.

### Docker recovery and completed local acceptance

The prior Docker fault is preserved as history. After recovery, all134 selected
integration tests passed, including the finalV5 migration and CrossServiceForeignKeysIT.
A fresh16-checkpoint real flow passed in one run; the same family cookie was accepted
by core, visit, report and notification without replacement. The paired restore and
failure/recovery checks passed again on the final schema.

A damaged JRE cache from this experiment was removed by exact cache IDs, without global
pruning. The rebuilt production runtime layer passed four network-disabled storage
probes with UID1001, a read-only root, tmpfs and private volumes, including packaged
PDF child-JVM validation and a second container reading the same file hashes. These
are storage/runtime probes, not a full container deployment or Kubernetes acceptance.
Two independent local6b pilots and six real polling observations passed on fixed final
JAR hashes. They are descriptive small samples, not SLA/cache/HPA acceptance. Owned
temporary resources were cleaned; the five pre-existing stopped containers were retained.
No app code changed during recovery, and existing identity-switch/hidden-tab issues
and cloud handoff requirements remain open.

### Publication baseline and merge prerequisites (historical, before local alignment)

Pre-publication verification matched all 189 changed paths to the validated source;
the preserved Redis fix and the original carelink working-tree test remained unchanged.
Local results include 2019 backend unit tests, 1300 frontend tests, 134 selected integration
tests, 8 service-table ownership checks, TypeScript checking and the frontend production build.
No application code was changed for publication; this delivery note is documentation only.

The separately fetched origin/main is now `59a91a3c021d0d85a76faf1dd64690c4b4d5c99e`,
six commits ahead of the shared `20e456a8` baseline. Its report-worker/KEDA work has not
been merged into or tested with this snapshot. In particular, main already contains
`services/report/src/main/resources/db/migration/V4__report_task_dispatch.sql`, whereas
this port adds `V4__visit_evidence_projection.sql`. **Do not merge both as V4.** Preserve
main's existing migration, allocate the next unused report version for the new evidence
projection, and re-run fresh/upgrade/restore and report-worker/event integration tests
on the combined version before merge or deployment. Do not edit or renumber a migration
that has already been applied to a shared database. Boards 2 and 4 should review the
combined worker, queue and storage configuration. Publishing this feature branch does
not enable KEDA, deploy infrastructure, or assert compatibility with the newer main.

### Local main alignment completed (2026-10-19)

Main report V4 is retained unchanged and this feature's evidence projection is now V5.
The earlier V4 collision described above is resolved in the local combined branch.
All 2021 backend unit tests and 160 selected integrations passed, with the real16-checkpoint flow, main V4->V5 upgrade and five API/worker checks.
See [scope, upgrade policy and limits](main-alignment-20261019.md). No deployment or formal6b result is implied.
