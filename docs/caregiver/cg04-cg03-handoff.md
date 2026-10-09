# CG04 incident reporting and CG03 execution basics

## Integration contract

CG04 reuses MG05 `IncidentService.reportByCaregiver` and its notification/escalation chain. It adds caregiver HTTP commands and own-report projections; it does not replace the manager workflow. `visit.application` calls `incident.application.CaregiverIncidentGateway`, preserving visit → incident dependency direction.

CG03 provides the missing source writes for existing FM03 reads. After real manager plan publication and primary-caregiver assignment, check-in initializes only the assigned version/node tasks, records arrival/start events, and tasks can become DONE, SKIPPED or REFUSED. It does not finish the entire CG03/CG05 scope.

| Owner | Integration to review | Unchanged boundary |
| --- | --- | --- |
| MG05 | Caregiver report → existing routing/queue/claim/resolve. Own receipt exposes live status/deadline, not manager internal notes. | Resolve never resumes/completes the Visit automatically. Staff notification recipients and SYS02 responsibility chain remain unchanged. Family messages now use the FM05 after-commit observer. |
| MG03/MG04 | Caregiver writes lock the parent Visit and advance its version, including task writes. V17 adds one task per non-null visit/node. | Scheduling/reassignment/cancellation methods remain; started work is not silently reassigned. No historical duplicates are deleted. |
| FM03 | Existing detail/timeline/tasks read real upstream facts. Only DONE counts; only APPLIED appears in the timeline. | No caregiver notes, outcome, GPS, manual location note, actor or rejection reason enters family projection. No completion event is fabricated. |
| FM05 | CG04 still calls MG05; shared EscalationService now registers raised/chain-exhausted facts for the existing after-commit Observer. The old direct family sender is removed in the same integration. | CG04 HTTP/authorization/version/receipt logic and staff alerts are preserved. `FamilyIncidentSourceIT` verifies real HTTP source commit/rollback/replay and consumer failures. Self-inbox authorization/resource/time fixes are covered by FamilyIncidentInboxIT; family bell navigation and post-render read/view are implemented; FamilyIncidentWorkflowIT and an isolated real-backend browser run verify the full CG04-to-family flow; see `docs/design/family/FM05-observer-integration.md`. |

## Business rules and migrations

- CG04: SCHEDULED reports require server time ≥ start; ARRIVED/IN_PROGRESS pause to EXCEPTION; further reports in EXCEPTION do not add self-transitions; terminal supplemental reports preserve the terminal state. CANCELLED/other assignments cannot create new reports. Own historical receipt remains readable after reassignment.
- Check-in: default start minus 30 minutes through end (inclusive); missing end uses Singapore day end. Late arrival after the existing 10-minute threshold can still check in within this window. `carelink.visit-execution.early-arrival` configures the early window.
- GPS or explicitly labelled manual location note; neither is a verified geofence. Server time controls checkedInAt. No continuous tracking.
- MG03's `service_type` is a task display name, not a stable clinical enum. A validated assigned plan node chooses the basic plan-task state strategy; unknown unlinked service types do not get guessed rules. This is an adaptation discovered during implementation.
- One check-in transaction records SCHEDULED → ARRIVED → IN_PROGRESS, clears waiting deadline and initializes missing assigned tasks. No GET creates tasks.
- PENDING tasks can be submitted once. SKIPPED/REFUSED need a factual reason and retain null completedAt; even all-DONE does not complete the Visit.
- Commands use immutable UUID/payload receipts and expectedVersion. Parent lock + READ_COMMITTED prevents stale replay reads. Buttons prevent concurrent UI writes; cancelling a browser request cannot undo a server transaction. No automatic POST retries.
- V15 belongs to the merged FM05 family alert delivery migration. V16: caregiver command receipt/index; V17: minimal check-in record and task uniqueness. Only new migrations, never edits to deployed migrations. Preflight legacy duplicates before applying V17 to a non-empty environment. Failing the migration is preferable to deleting unknown facts.
- The unmerged caregiver migrations were renumbered from V15/V16 to V16/V17 when FM05 reached main first. An isolated preview database that already applied the old caregiver numbering needs a separately approved data-preserving migration/rebuild procedure before running this version. Do not run Flyway repair, delete its volume, or alter shared migration history automatically.
- Authorized state rejection is recorded after the command transaction releases its locks. Permission denials do not enter the family's state timeline. Command audit/receipt stays free of care narrative and GPS text.

These changes need team review before merge, especially schema/notification/state assumptions. This file is a handoff draft; it has not been sent to teammates. Approval to develop does not establish that team review happened.

## HTTP

Implemented definitions are in `docs/api/openapi.yaml`; superseded draft operations point there.

- POST `/api/incidents`: caregiver only; body visitId/category/severity/description/expectedVersion/clientRequestId. 201 new / 200 same successful command.
- GET `/api/caregivers/me/incidents`: own page, optional visit filter, size 1–50.
- GET `/api/caregivers/me/incidents/{id}`: own safe receipt; foreign/missing uniformly unavailable.
- POST `/api/visits/{visitId}/check-in`: command identity and GPS/manual location.
- POST `/api/visits/{visitId}/tasks/{taskId}/complete`: command identity, DONE/SKIPPED/REFUSED and bounded text.
- GET `/api/visits/{visitId}/tasks`: server-selected caregiver or original FAMILY projection. A projection query cannot override role.
- GET work-pack: backward-compatible `execution` context (server time/window, allowed actions, checked times, location source).

The receipt version/state identifies the original command result, not a promise about the current Visit. Always reload current work-pack after saving/replaying. A timeout/503 can be uncertain: first inspect own reports/tasks, then explicitly retry the identical payload/key if needed.

## Verification and demonstration

Run frontend lint/coverage/build, backend `./mvnw clean verify -Pintegration`, and the independent full caregiver MySQL acceptance workflow. Do not lower coverage gates. `CaregiverVisitExecutionWorkflowIT` uses real HTTP manager plan publication and assignment before caregiver check-in/task/report and FAMILY reads; SQL is limited to synthetic identity/binding setup and independent negative fixtures. The fault test forces receipt storage failure and checks complete rollback. Existing escalation, family access, roster and CG01 tests remain in the full suite.

For a visual demo, use the isolated local CareLink demo and separate caregiver/manager/family sessions. Create current-day visits through normal plan/assignment operations; old slice-1 dates are not an execution demo. Read the manual steps in the external 002/003 plans and record observed results, not guessed screenshots.

Not included in CG04/CG03 basic execution: vital signs, evidence upload, check-out/CG05, independent elder confirmation, restoring an exception visit, offline commands or new notification channels. SYS03 assigned-caregiver missed-check-in scanning is delivered in the subsequent batch; see [SYS03 handoff](sys03-missed-check-in-handoff.md). In-app notification rows are not proof of SMS/email delivery.
