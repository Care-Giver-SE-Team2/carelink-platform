# SYS03 assigned-caregiver missed check-in — handoff

## Integration contract

SYS03 now invokes the existing MG05 incident routing and SYS02 countdown; FM05 receives the existing after-commit raised/unresolved facts. There is no new public scan/clock API or second family sender.

Eligible: assigned caregiver, `SCHEDULED`, no check-in, start within the last 24 hours (inclusive). Trigger strictly **after** `scheduledStart + carelink.late-arrival-threshold` (default 10 minutes), measured in Singapore time. A visit past its scheduled end can still require review; this does not reopen check-in.

One `visit_missed_check_in_trigger` row per Visit lifetime references one `SYSTEM_MISSED_CHECKIN / SERVICE / MEDIUM` incident. The narrative distinguishes an assigned-but-absent caregiver from MG03 unassigned and MG04 leave reminders. Other incidents do not suppress SYS03. Resolved incidents, reassignments, new versions and restarts do not reset this ledger. Moving creates a new Visit with its own eligibility.

The alert transitions the locked Visit from `SCHEDULED` to `EXCEPTION`, advances its version once and writes an APPLIED state transition with a null system actor. Both stale and refreshed check-in requests are rejected (409); no check-in record or executable tasks are initialized. Managers can claim/resolve the Incident through the existing console, but resolving it does not resume, finish or check out the Visit. This supersedes the earlier retain-SCHEDULED/late-check-in contract.

The Visit gateway delegates to `IncidentService.raiseForMissedCheckIn`, which requires the caller's transaction, saves the Incident and REPORTED timeline and calls `routeNewIncident` exactly once. Only the existing routing registers family events; SYS03 never publishes a second raised event or directly creates a family notification.

Staff routing is unchanged: managers and the existing recent-caregiver lookup, not a new guaranteed recipient rule for the current assignee. Family IN_APP alerts and personal view/awareness receipts follow existing binding authorization. Current caregivers cannot access the manager/system incident detail through their own CG04 reports endpoint.

## Transactions and competing writers

- Candidates are bounded, keyset-paged by start/id with a fixed round time boundary. A failed record advances the cursor and retries next round, rather than starving later pages.
- Each record owns a READ_COMMITTED transaction and a refreshed parent Visit write lock. Re-check current assignment/status/time and persistent ledger **after** locking.
- Visit EXCEPTION, parent version, system state transition, Incident/timeline, staff routing and ledger commit together. Failure rolls all of them back; registered family facts are not delivered on rollback.
- A family consumer fails after source commit: the source remains committed, the existing FM05 failure outcome is recorded, other recipients continue. The scanner does not resend or create another incident.
- Existing MG04 reassignment/call-off/move retains JPA optimistic locking; stale writes roll back and map to retryable HTTP 409. No decision algorithm is replaced.
- An assigned SYS03 EXCEPTION is not open to existing MG04 ordinary reassignment/call-off/move. A refreshed manager request is also rejected. Recovery/replacement needs an explicit contract from the manager owner; this batch does not add a manager recovery use case. Plan cancellation also skips this exceptional Visit after locking/re-reading it.
- Plan cancellation locks/re-reads parent Visits in id order. Plan publication/stop and roster refresh are separate existing transactions: **the plan commits first**. Existing MG03 intentionally retains already-due visits for missed-attendance review; stopping a plan is not retroactive cancellation of overdue work. Future cancellation remains supported. A refresh failure still follows the existing logged/nightly-retry behavior.

## Configuration and migration

`carelink.missed-check-in`: `enabled=true`, `scan-interval=PT60S`, `scan-initial-delay=PT30S`, `lookback=PT24H`, `batch-size=200` (1..1000). Durations must be positive; shared late threshold must be nonnegative. Disable with `enabled=false`. No private narratives are logged by this scan.

V18 creates the trigger ledger and scan index. V1–V17 are unchanged: V15 family delivery, V16 caregiver command receipts, V17 check-in/task uniqueness. Do not repair or reuse the legacy demo database with old caregiver V15/V16. Use the independent SYS03 demo described below.

## Isolated local demonstration

Use `scripts/start-sys03-demo.ps1` and then `scripts/prepare-sys03-demo.ps1`. Default build uses the production Dockerfile. Local cached fallback: build a **clean** backend jar, copy freshly built `frontend/dist` into `backend/target/classes/static`, package again, then start with `-CachedRuntime`. This fallback requires the existing `carelink:caregiver-demo` runtime image; do not use it for CI/staging. Generated assets belong in target, not source.

URL `http://localhost:8082`; Compose project `carelink-sys03-demo`, new `sys03-db` volume. Legacy 8081 remains preserved. LOCAL ONLY credentials:

| Role | Username | Password |
| --- | --- | --- |
| Manager | demo-exec-manager | Demo#2026 |
| Caregiver | demo-exec-cg-a | Demo#2026 |
| Family | demo-exec-family | Demo#2026 |

The demo uses **1-minute lateness / 5-second scan**, not production 10m/60s. Preparation inserts fictional identities/bindings only; manager HTTP assignment and plan publication generate the Visit. Reruns add a new demonstration plan; they do not fabricate execution/notification facts.

1. Record the Visit id/start printed by preparation. Check the same Visit in manager roster and caregiver My schedule/work pack.
2. Leave the caregiver unchecked-in. After start +1m and one scan, open manager Incidents: assigned-but-not-checked-in, SERVICE/MEDIUM, system reporter.
3. Refresh twice: no second SYS03 incident for that Visit.
4. Family bell → incident detail → view/awareness. Notification read, incident view and awareness are separate; other families remain independent.
5. Caregiver refreshes work pack: status is **Exception**, no Check in button and execution is blocked. A check-in request with the current version is still 409; checkedInAt stays null and tasks are not initialized.
6. Manager claims and resolves the incident; the Visit stays EXCEPTION and check-in remains blocked. No recovery is implied by closing the incident.
7. Repeat with a timely check-in: no SYS03 alert. Cancel/reassign/move examples and both commit orders are additionally covered by real MySQL controlled-concurrency tests.

## Verification record

The earlier results below are historical evidence for head `6213ad5`, which retained SCHEDULED and allowed legal late check-in. They do not verify the revised EXCEPTION contract. Current implementation/test results are recorded separately in the external plans and the current PR; the new suite adds IncidentService single-route/mandatory-transaction tests and state/timeline/parent-write rollback checks. Existing historical demo alerts are not retroactively rewritten.

Local full verification on 2026-10-08 before the UTC portability correction: 1,275 unit/architecture tests and 757 real MySQL integration scenarios, zero failures/errors/skips; coverage checks passed (overall backend line coverage 98.17%). The current SYS03 suite includes 15 workflow and 13 controlled-concurrency cases, including source rollback, family-consumer failure, both writer orders, UTC cursor mapping and restart deduplication. Negative/boundary database fixtures are separate from the manager-published positive workflow.

Linux UTC CI exposed inconsistent time representations in the new scanner and SQL boundary fixtures. Main's existing Hibernate Visit mapping binds/reads LocalDateTime through Timestamp; inferred scanner/ledger parameters and the raw SQL fixture helper instead bound LocalDateTime directly. Mixing these representations missed manager-published visits and broke keyset advancement. The intermediate typed-retrieval change in 250f88c was insufficient and is superseded: candidate bounds/cursors and ledger dates now explicitly bind Timestamp and read with main's existing Timestamp convention; negative/boundary Visit fixtures use that convention too. Parent command updates also explicitly bind Timestamp, preserving existing deadlines when SYS03 only advances a version and keeping check-in/checkout compatible with the JPA reader. The scanner retains a fast-failure guard for non-advancing cursors. No existing Visit mapping, global time-zone setting or historical data is reinterpreted.

The regression checks real manager-published starts, two keyset pages, one scan and persisted start/due/trigger times. Related suites run with the **whole test JVM** in UTC and Singapore separately; changing the default zone halfway through an existing datasource lifecycle is not treated as realistic portability evidence. Full current-commit CI evidence is recorded in the external plans. CI timeouts, assertions and quality gates remain unchanged. This preserves main's current JDBC representation; a project-wide move to raw wall-clock storage would require coordinated persistence/data migration and is outside this batch.

Frontend: 75 files / 911 tests passed; line coverage 91.85%, build passed. Windows scoped source lint passed with three existing warnings; remote CI runs the unchanged full lint command. OpenAPI formal/draft specifications and all local references passed full validation. No CI gates were weakened.

Browser on the isolated V17→V18 upgrade: manager publication generated Visit #3 (12:02 SGT); the real timer raised incident #2 after 12:03. Manager queue/detail showed a system SERVICE/MEDIUM fact and repeated refreshes showed no duplicate. Family bell→detail recorded separate view/awareness; the caregiver legally checked in late and tasks initialized while the incident stayed OPEN. The manager then claimed/resolved it; the Visit remained IN_PROGRESS, not completed. Visit #5 checked in before its threshold and still had no SYS03 fact after the threshold. A third published Visit #7 was cancelled by the existing manager plan-stop page before its start.

Cross-threshold cancellation/restart observations and current PR/CI outcomes are recorded in the external numbered plans. Screenshots/logs are local ignored project artifacts, not committed personal evidence. Browser checks used one fictional family; two independent families, revoked access and reassign/move races were proven by real MySQL tests, not claimed as additional browser actions. Remaining platform limits: FM05 uses in-process after-commit dispatch, not a durable source outbox or guaranteed restart resend; this batch does not change that design.
