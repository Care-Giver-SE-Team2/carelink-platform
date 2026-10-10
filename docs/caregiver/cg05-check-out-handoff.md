# CG03 late check-in / CG05 self check-out — handoff

## Rules

- The shared late threshold remains ten minutes by default; strictly later than start+threshold is late. SYS03 keeps its once-per-Visit incident and existing MG05/SYS02/FM05 route, without pausing a scheduled service. No extra sender, punishment, approval or automatic Incident resolution is added.
- Check-in still opens thirty minutes before start and closes at scheduled end (inclusive), or Singapore day end when no end exists. Safe legacy SYS03-only compatibility is documented in the SYS03 handoff; all other exceptions remain blocked.
- A caregiver assigned to a genuinely checked-in IN_PROGRESS Visit may check out directly. Pending tasks, missing evidence and missing vitals do not block departure. Original task/health/evidence facts are not changed. No minimum duration or planned-end restriction applies to departure.
- Check-out writes COMPLETED, not VERIFIED/AUTO_CLOSED. Elder review is independent afterward. report.dataComplete may still be false, and existing frozen reports are not rewritten.

## HTTP and transaction

`POST /api/visits/{visitId}/check-out`, CAREGIVER session + CSRF.

Request: `expectedVersion` (nonnegative integer), `clientRequestId` (UUID). Clients do not control departure time, status or assignment.

Response: `visitId`, `visitVersion`, `savedState`, `checkedInAt`, `checkedOutAt`, `replayed`. Times are server Singapore local date-times without an offset; arrival/departure are stored/replayed at second precision. Arrival window validation uses the precise server clock before truncating the persisted arrival to avoid MySQL rounding it into the future and imposing an accidental extra-second stay. Receipt time uses the same Timestamp binding as Visit persistence, including UTC JVMs.

The existing executor checks current assignment and locks the parent. Identical actor/key/payload receipt replays before version/state validation, returning the original result. New writes validate state/version/time, save Visit/version+1, append one APPLIED IN_PROGRESS→COMPLETED transition, audit and receipt in one transaction. A failure rolls all facts back. Different keys cannot check out twice; reusing a key with changed action/version/body conflicts.

403 covers role/assignment/CSRF, 404 missing Visit, 400 command validation, 409 state/time/version/key conflict, 503 unconfirmed storage result. After timeout/503, inspect the work pack and retry only the identical request if needed; never automatically POST a new UUID.

## Page and downstream boundary

The English work pack shows `Check out` and non-blocking pending-task/evidence notices. Save desired drafts first: task/new-health writes stop after completion. Missing evidence cannot yet be uploaded/verified in this page. Server allowedActions is authoritative; each command rechecks in its transaction.

COMPLETED and real times feed existing EL01, FM03 and reports. A supplemental CG04 report retains completed state. Ordinary CG04-first EXCEPTION still prevents check-out. Manager/elder/family/report workflow implementations and notification audience rules are not changed.

No schema migration is added by this slice. The synchronized main baseline is d16f7a3 (V1–V25), including teammates' V22–V25; no existing migration was edited. Health summaries/history, original alerts and task statuses are preserved. Main now supplies standalone FM08 visits with service instructions and one task initialized on check-in. End-to-end acceptance of those visits with this local check-out remains pending, rather than an absent basic execution implementation. Precise SYS03 caregiver recipients, authorized system-alert navigation, health-note report projection and evidence upload remain separate handoff items.

## Verification

New real MySQL workflow/concurrency suites use manager-published Visit fixtures and caregiver HTTP check-in/check-out. SQL creates fictional identities and explicit legacy/negative fixtures only, never positive completion. They cover both writer orders, same-key retries, rollback, actual-time precision, pending materials, health preservation and legacy whitelist rejection. Local results and manual demonstration observations are recorded in external 008-plan.md; no remote CI result is implied by local tests.

Local acceptance finished on 2026-10-10: final-source 1,465 unit/architecture and 70 affected MySQL cases passed under UTC, plus 63 check-out/check-in/SYS03 cases under an Asia/Singapore JVM; frontend 1,034 cases passed. Coverage gates, production build, source lint and API reference validation passed. Earlier full integration evidence predates the final small precision additions and is recorded separately. No remote CI, commit, push or merge was performed for this slice.

Current source diagrams are `docs/design/caregiver/CG03-basic-execution-state.puml` and `UC-CG03-CG05-activity.puml`. Earlier exported activity PNGs are historical design artifacts, not proof of current implemented behavior.

## Main synchronization — 2026-10-10

Main and the local feature branch were fast-forwarded to d16f7a3; uncommitted changes were backed up and restored. Three textual conflicts were combined, preserving service instructions, standalone task initialization and check-out/legacy-resume rules. The new upstream execution unit test was adapted to the combined constructor. This did not alter manager/family business workflows.

On this combined baseline, 1,493 unit/architecture tests and 103 caregiver frontend tests passed; the production frontend build passed. The selected 83 MySQL cases could not start because the local Docker Linux engine was unavailable, so integration acceptance is NOT complete. Earlier UTC/SGT MySQL results above apply to e2b6b5e, not to this new baseline. No remote CI was run for these uncommitted changes; main's successful pipeline does not validate this local combination. Existing localhost:8084 demo data/image were not rebuilt or reset.
