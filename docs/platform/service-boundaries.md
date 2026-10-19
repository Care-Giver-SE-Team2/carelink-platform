# Service boundaries

What has to change when visit, report and notification move out of core: which schema owns each table, which
foreign keys cross a schema, what core offers the other services in place of today's in-process calls, and which
SQL still reads another service's tables.

Generated from the code at `c124099` (Flyway migrations V1–V25, 50 tables). Owner: core and shared parts. Each
service owner refines their own section when they split the service.

**Where the move stands.** visit, report and notification run as services of their own. visit and report reach
core only through ports of their own (`visit/application`, `report/application`), whose adapters call `CoreApi`
(`…/infrastructure/core`); report reaches visit through `VisitApi`. Rostering and incident, which stay in core,
reach visit through ports of theirs whose adapters call `VisitApi`. The calls in sections 3 and 4 are all done, the
SQL and writes in section 5 are all replaced, and each service has a database of its own (section 9). Section 7
lists what the move changes for the commands visit and core take part in together - visit's incidents, and
re-rostering's changes to visits, which are a saga - and section 8 the paths the Ingress sends to each service.

---

## 1. Which schema owns each table

Each schema is now a database of its own (section 9): `carelink`, `carelink_visit`, `carelink_report`,
`carelink_notification`.

| Schema | Tables | By module |
|---|---|---|
| `core` | 31 | **careplan**: `care_plan`, `care_plan_node`, `care_plan_node_visit`, `care_plan_required_credential`<br>**identity**: `app_user`, `user_role`<br>**incident**: `family_alert_delivery`, `family_alert_event`, `family_alert_window`, `incident`, `incident_acknowledgement`, `incident_log`, `spot_check`<br>**profile**: `care_service_application`, `caregiver`, `credential`, `credential_type`, `elder`, `elder_family_binding`, `elder_primary_caregiver`, `family_member`, `intake_application`<br>**rostering**: `absence_report`, `caregiver_availability`, `caregiver_preference`, `roster_change`, `rostering_candidate`, `rostering_candidate_check`, `rostering_constraint`, `rostering_run`<br>**shared**: `audit_log` |
| `visit` | 11 | `caregiver_command_receipt`, `elder_confirmation`, `visit`, `visit_assignment`, `visit_check_in_record`, `visit_evidence`, `visit_health_record`, `visit_missed_check_in_trigger`, `visit_state_transition`, `visit_task`, `vital_sign` |
| `report` | 6 | `caregiver_review`, `report`, `report_amendment`, `report_basis`, `value_added_service`, `value_added_service_request` |
| `notification` | 10 | **inbox**: `notification`, `notification_subscription`<br>**openplatform** (notification's V2 and V3): `provider_application`, `provider`, `provider_webhook_endpoint`, `webhook_delivery`, `webhook_delivery_attempt`, and who may call the Open Platform: `provider_portal_user`, `provider_client`, `provider_token_key` |

Seven tables have no JPA entity and are assigned by the code that writes them: `user_role` (identity),
`family_alert_*` (incident) and `caregiver_command_receipt`, `visit_check_in_record`,
`visit_missed_check_in_trigger`, `visit_health_record` (visit).

`family_alert_*` holds FM05's alert deliveries. It stays in core while the incident module writes it; if the
notification service takes over FM05 delivery, the three tables move with it.

## 2. Foreign keys that cross schemas

17 of the 65 foreign keys cross a schema. Platform migration V3 dropped them on 2026-10-11, before the split, so
that every service is built the way it will run after the split. The column stays and keeps the id, and so does
the index MySQL made for the key. The service that owns the column checks the reference in code before it writes
an id it has not just read. For example, visit asks core whether the elder exists before it creates a visit.
`CrossServiceForeignKeysIT` fails the build if a migration adds such a key again, for example one that an
upstream sync brings in; drop it in the next platform migration.

| From (schema) | Column | To (schema) | Added in |
|---|---|---|---|
| `visit` (visit) | `elder_id` | `elder` (core) | V2 |
| `visit` (visit) | `caregiver_id` | `caregiver` (core) | V2 |
| `visit` (visit) | `absence_id` | `absence_report` (core) | V2 |
| `visit_assignment` (visit) | `caregiver_id` | `caregiver` (core) | V2 |
| `elder_confirmation` (visit) | `elder_id` | `elder` (core) | V2 |
| `visit_missed_check_in_trigger` (visit) | `incident_id` | `incident` (core) | V18 |
| `visit_missed_check_in_trigger` (visit) | `triggered_caregiver_id` | `caregiver` (core) | V18 |
| `rostering_candidate` (core) | `visit_id` | `visit` (visit) | V2 |
| `roster_change` (core) | `visit_id` | `visit` (visit) | V12 |
| `incident` (core) | `visit_id` | `visit` (visit) | V2 |
| `report` (report) | `elder_id` | `elder` (core) | V2 |
| `value_added_service_request` (report) | `elder_id` | `elder` (core) | V2 |
| `value_added_service_request` (report) | `visit_id` | `visit` (visit) | V2 |
| `caregiver_review` (report) | `family_member_id` | `family_member` (core) | V2 |
| `caregiver_review` (report) | `elder_id` | `elder` (core) | V2 |
| `caregiver_review` (report) | `caregiver_id` | `caregiver` (core) | V2 |
| `notification_subscription` (notification) | `elder_id` | `elder` (core) | V2 |

## 3. What core offers visit and report

visit and report call the replacements below; the first column is the call they replaced. Internal endpoints live under `/internal/v1/`. The Ingress routes only the public paths (`/api/**`), never
`/internal`, so they are reachable only inside the cluster. Responses carry only the fields the callers use today.

The contract is written once, as the Java interface `CoreApi` in `libs/core-api`: core implements it (the
`Internal*Controller` classes, checked against the interface by `CoreApiContractTest`), and the other services
call it through the same interface. [building-a-service.md](building-a-service.md) shows how to call it.

| Call today (core type · method) | Called from | Replacement |
|---|---|---|
| `IdentityService.require(username)` → `AppUser` | report (5 classes), visit (1) | **No call.** The signed-in user (id, username, display name, roles) comes from the shared session, through the shared library |
| `FamilyAccessQuery.readableElderIds`, `requireReadableElder`, `requireWritableElder` | visit (5), report (3) | `GET /internal/v1/family-access/{username}/elders` → `{ elderIds }`; `GET /internal/v1/family-access/{username}/elders/{elderId}?access=READ\|WRITE` → 204, or 403 as today, which the caller passes on unchanged |
| `FamilyReadAudit.read(…)` | visit (5), report (1) | **No call.** The calling service records the family's read in its own `audit_log`, through the shared library |
| `CaregiverDirectory.findPublicProfile` | visit, report | `GET /internal/v1/caregivers/{id}/public-profile` → `{ id, fullName, dialects }` |
| `CaregiverDirectory.listPublicCredentials` | visit | `GET /internal/v1/caregivers/{id}/public-credentials` → `[{ id, caregiverId, credentialTypeId, credentialTypeName, issuingBody, validFrom, expiryDate, status }]` |
| `CaregiverWorkDirectory.require(username)` → `Profile` | visit (5) | `GET /internal/v1/caregivers/by-username/{username}` → `{ id, userId, fullName, phone, sector, dialects, status }` |
| `CaregiverWorkDirectory.elder(elderId)` → `ElderView` | visit | `GET /internal/v1/elders/{id}/caregiver-view` |
| `CaregiverWorkDirectory.alerts(caregiverId, today)` | visit | `GET /internal/v1/caregivers/{id}/credential-alerts?today=` |
| `ProfileService.requireElderByUserId(userId)` → `Elder` (only `id` is used) | report (2), visit (1) | `GET /internal/v1/elders/by-user/{userId}` → `{ elderId }` |
| `FamilyMemberRepository.findByUserId` → `FamilyMember` (only `id` is used) | report (2) | `GET /internal/v1/family-members/by-user/{userId}` → `{ familyMemberId }` |
| `FamilyAlertRecipients.familyMemberIds`, `resolve` | report (1) | `GET /internal/v1/elders/{id}/family-members`; `GET /internal/v1/elders/{id}/family-members/{familyMemberId}/alert-recipient` |
| `PrimaryCaregiverLookup.findRosterableCaregiverId` | report (1) | `GET /internal/v1/elders/{id}/primary-caregiver` → `{ caregiverId }`, or 404 |
| `VisitPlanReader.read(planId, elderId)` → `Snapshot` | visit (2) | `GET /internal/v1/care-plans/{planId}/snapshot?elderId=` |
| `CaregiverIncidentGateway.report`, `own`, `list` | visit (1) | `POST /internal/v1/incidents/caregiver-reports`; `GET /internal/v1/incidents/{id}?actor=`; `GET /internal/v1/incidents?visitId=&actor=&page=&size=` |
| `MissedCheckInIncidentGateway.raise(…)` → incident id | visit (1) | `POST /internal/v1/incidents/missed-check-ins` → `{ incidentId }`. Synchronous because visit stores the id; the missed check-in scan retries on failure |
| `IncidentService.createElderServiceDispute(…)` | visit (`VisitService`) | `POST /internal/v1/incidents/service-disputes` → `{ incidentId }` |
| `MissedCheckInPauseEvidence.isSoleIncident(elderId, visitId, incidentId)` | visit (`MissedCheckInResumeService`) | `GET /internal/v1/incidents/{incidentId}/sole-missed-check-in?elderId=&visitId=` → `{ sole }`: is this incident the visit's only one, raised by the missed check-in scan? |
| SQL on `app_user`, `user_role` in `JdbcRecipientDirectory` (is the account behind a session still allowed, and with which roles) | notification | `GET /internal/v1/accounts/{username}` → `{ userId, username, displayName, enabled, roles }`, or 404 |
| SQL on `app_user`, `user_role` in `NotificationTableValueAddedManagerAlert` (every enabled manager) | report | `GET /internal/v1/accounts/with-role/{role}` → `{ userIds }` |
| SQL on `caregiver` in `NotificationTableValueAddedNotifier` (the caregiver's account, which a message is addressed to) | report | `GET /internal/v1/caregivers/{caregiverId}/account` → `{ userId }`, or 404 |
| SQL on `elder`, `elder_primary_caregiver`, `caregiver`, `care_plan` in report's facts source (the elder a report describes) | report | `GET /internal/v1/elders/{elderId}/report-profile` → the profile, the primary caregiver and the latest published plan, or 404 |
| SQL on `absence_report` in report's value-added assignment (is the caregiver away that day) | report | `GET /internal/v1/caregivers/{caregiverId}/on-leave?day=` → `{ onLeave }` |
| `VisitCover.options(visitId)`, `cover(…)` | report (2) | `GET /internal/v1/visits/{visitId}/cover-options`; `POST /internal/v1/visits/{visitId}/cover`. Rostering stays in core and calls visit in turn |

The caller keeps the port it has today and swaps the adapter. For example, visit keeps its own `FamilyAccess`
interface, and the implementation becomes a call through `CoreApi`, which has connect and read timeouts. The
business code that calls the port does not change. The library does not retry: a read can simply be repeated, and
a `POST` is repeated only by a caller that knows it is safe (the missed check-in scan tries again on its next run).

## 4. What visit offers core and report

Rostering and incident, in core, and report call visit. Their calls are written down the same way as core's: the
Java interface `VisitApi` in `libs/visit-api`. visit serves it (the `InternalVisit*Controller` classes in
`visit/controller`, checked against the interface by `VisitApiContractTest`; `VisitApiIT` runs the reads incident
needs against MySQL).

| Call today | Called from | Replacement |
|---|---|---|
| `VisitReassignment.find` | rostering | `GET /internal/v1/visits/{visitId}` → `{ visitId, elderId, caregiverId, carePlanId, serviceType, start, end, status, absenceId }`, or 404 |
| `VisitReassignment.unstartedFor` | rostering | `GET /internal/v1/visits/unstarted?caregiverId=&from=&until=` |
| `VisitReassignment.bookingsBetween` | rostering | `GET /internal/v1/visits/bookings?from=&until=` |
| `VisitReassignment.finishedVisitsWith` | rostering | `GET /internal/v1/visits/finished-with?elderId=&since=&until=` → `{ caregiverId: count }` |
| `VisitReassignment.reassign`, `markUncovered`, `callOff` | rostering; report (`callOff`) | `POST /internal/v1/visits/{visitId}/reassignment`, `…/uncovered`, `…/call-off` → 204. Optional header `Idempotency-Key`: made once per key (section 7.2) |
| `VisitReassignment.moveTo` | rostering | `POST /internal/v1/visits/{visitId}/move` → `{ visitId }` of the new visit; the same one again for the same `Idempotency-Key` |
| `VisitScheduling.schedule` | rostering | `POST /internal/v1/visits/schedule` → `{ created, covered }` |
| `VisitScheduling.cancelUntouchedFrom` | rostering | `POST /internal/v1/visits/cancel-untouched` → `{ count }` |
| `VisitScheduling.findUncoveredStarted`, `markUncoveredAsException` | rostering | `GET /internal/v1/visits/uncovered-started?since=&now=`; `POST /internal/v1/visits/{visitId}/uncovered-exception` → `{ marked }` |
| `UpcomingAssignments.unstartedBetween` | rostering | `GET /internal/v1/visits/upcoming-assignments?from=&until=`. Done: rostering's `BookedVisits` port has both adapters, the worked example |
| `StandaloneVisits.schedule`, `find` | report | `POST /internal/v1/visits/standalone` → `{ visitId }`; `GET /internal/v1/visits/{visitId}/state`, or 404 |
| `VisitScheduleQuery.caregiverIdsForElder`, `hasAssignedVisit` | report | `GET /internal/v1/visits/caregivers-of-elder?elderId=`; `GET /internal/v1/visits/assigned?elderIds=&caregiverId=` → `{ assigned }` |
| SQL on `visit` in `JdbcSpotCheckLookups` | incident | `GET /internal/v1/visits/{visitId}`; `GET /internal/v1/visits/upcoming?elderId=&from=&until=` |
| SQL on `visit` in report's value-added assignment (does the caregiver have a visit at that time) | report | `GET /internal/v1/visits/caregiver-busy?caregiverId=&from=&until=` → `{ busy }`. Its times are bound the way JPA writes them, which the SQL it replaces did not do |
| SQL on `visit` in `NotificationTableAlert` (the elder's latest caregiver) | incident | `GET /internal/v1/visits/latest-caregiver?elderId=` → `{ caregiverId }`, or 404 |

Times are ISO date-times on Singapore's wall clock, as visit stores them. Each caller keeps a port of its own and
an adapter that calls `VisitApi`: rostering's `BookedVisits`, `VisitReassignment` and `VisitScheduling`
(`rostering/domain/repository`, adapters in `rostering/infrastructure/visit`), and incident's spot-check visits and
latest caregiver (`SpotCheckVisits` in `incident/infrastructure/lookup`, `LatestCaregivers` in `…/notify`).
Rostering's `BookedVisits` is the worked example ([building-a-service.md](building-a-service.md), section 4). The
[event catalogue](event-catalogue.md) decides which facts travel as events instead: report's reads of the visit
table become visit's events there, and `AbsenceReported` is not built.

In core's tests visit is a stand-in (`testsupport/VisitStandIn`), which keeps the visits in memory and answers the
way visit's services do; in visit's tests core is one (`testsupport/CoreStandIn`), which reads the people and plans
the tests lay out in core's tables. Each is in place of the client for every test that starts the application.

## 5. SQL and writes that reach another service's tables

### 5.1 Writes to the notification table → `NotificationRequested` event

Done. Nine classes wrote into `notification` directly; each now publishes one `NotificationRequested` event per
recipient, in the transaction that decided to send it, and notification's `NotificationRequestedHandler` keeps
the message. Eight implement a port, so only the adapter changed and the business code that raises the alert
stayed as it was; the ninth and tenth listen for careplan's `CarePlanPublished` and profile's
`FamilyBindingRequested` after the change commits, and write their events in a transaction of their own. `NotificationRequested.inApp` cuts titles and bodies to what the inbox keeps.

| Module | Class |
|---|---|
| profile | `NotificationRequestedCredentialAlert` (the worked example), `CarePlanPublishedFamilyNotifier`, `FamilyBindingRequestedNotifier` |
| incident | `NotificationRequestedIncidentAlert`, `NotificationRequestedSpotCheckAlert`, `JdbcFamilyAlertDeliveryStore` |
| rostering | `NotificationRequestedAbsenceAlert`, `NotificationRequestedRosterAlert` |
| report | `NotificationRequestedValueAddedManagerAlert`, `NotificationRequestedValueAddedNotifier` |

Two of them used to look in `notification` to see what they had already sent; each now notes it on its own
record. The spot check alert notes when it asked the family (`spot_check.family_asked_at`, which the reminders
read), and report notes a request's one reminder (`value_added_service_request.family_reminded_at`).
FM05's alert window no longer points at a notification row: its message is an event, written with the window
and the delivery outcome. core's tests read the messages core asked for through a test-only view over the
outbox, `requested_notification` (`src/test/resources/db/platform`).

### 5.2 Reads of another service's tables

| Class | Reads from other schemas | Direction of the fix |
|---|---|---|
| report `KeptFactsSource` (was `ReportFactsJdbcSource`) | none now; it read `care_plan`, `caregiver`, `elder`, `elder_confirmation`, `elder_primary_caregiver`, `incident`, `incident_log`, `roster_change`, `spot_check`, `visit`, `visit_evidence`, `visit_task`, `vital_sign` | Done: report keeps the facts in its own tables from visit's and core's events (`ReportFactHandlers`, `JdbcKeptFacts`), and asks core for the elder (`CoreApi.elderReportProfile`) and the caregivers' names |
| report `ApiValueAddedVisitAssignment` (was `JdbcValueAddedVisitAssignment`) | none now; it read `absence_report`, `visit` | Done: `CoreApi.onLeave` and `VisitApi.caregiverBusy` |
| report `NotificationRequestedValueAddedManagerAlert` | none now; it reads `app_user`, `user_role`, `elder` and `caregiver` through `CoreApi` | Done |
| report `NotificationRequestedValueAddedNotifier` | none now; the caregiver's account through `CoreApi.caregiverAccount`, the names through `CoreApi` | Done |
| notification `JdbcNotificationInbox` | none now | Done: each message keeps the elder its event names (`notification.elder_id`), and the family rule takes the elders `CoreApi.readableElders` answers |
| notification `JdbcRecipientDirectory` | none now | Done: `CoreRecipientDirectory` asks `CoreApi.findAccount` |
| incident `JdbcSpotCheckLookups` | none now | Done: the time of the last ask is on the spot check, and the visits are calls to visit (section 4) |

visit's `CaregiverCommandStoreAdapter` writes `audit_log` through the shared audit classes. After the split, each
service keeps its own `audit_log` in its own schema.

No service reads or writes another's tables, and each has a database of its own (section 9).

## 6. Tables the platform adds

The platform's own tables go in a separate migration location, `db/platform`, with their own history table,
`flyway_platform_history`. The application's migrations number on from V25, so the next one is V26. Because the two version
sequences live in different history tables, they can never collide.

A library can ship platform migrations too: Flyway finds `db/platform` in every jar on the classpath. Each
platform migration takes the next free version, listed here.

| Version | Table | Schema | From | Purpose |
|---|---|---|---|---|
| V1 | `shedlock` | core | core | Runs each core scheduled job on one replica only |
| V2 | `outbox_event` | every service that publishes events | `libs/events` | Events written in the same transaction as the business data, then published |
| V2 | `consumed_message` | every service that handles events | `libs/events` | Ignores a message it has already handled |
| V3 | none: drops the 17 foreign keys of section 2 | the shared schema | core | No foreign key joins two services' tables |
| V4 | `family_alert_window.first_notification_id` may be empty | core | core | A family member's FM05 message is an event, so core has no row id to keep |
| V5 | `spot_check.family_asked_at` | core | core | When the family was last asked, for the reminders; it was read from `notification` |
| V6 | `value_added_service_request.family_reminded_at` | report | core | A request's one reminder, noted on the request; it was read from `notification` |
| V7 | `notification.elder_id` | notification | core | The elder a message is about, as its event names it, for the family rule; filled once for the messages kept before |
| V8 | `incident_request` | core | core | Which incident each keyed request from visit raised, so a retry gets the same one (section 7) |
| V9 | `report_visit`, `report_visit_task`, `report_vital_sign`, `report_elder_confirmation`, `report_incident`, `report_incident_log`, `report_spot_check`, `report_roster_change` | report | core | The facts a report is made from, kept from visit's and core's events (event catalogue, section 5) |
| V10 | none: fills V9's tables once from the shared tables | report | core | The first deployment's copy. It is safe to run again, and is run again after loading demonstration data into the shared tables |
| V11 | none: drops `notification` and `notification_subscription` | core | core | notification has a database of its own (section 9) |
| V12 | none: drops visit's eleven tables | core | core | visit has a database of its own (section 9) |
| V13 | none: drops report's six tables and V9's | core | core | report has a database of its own (section 9) |
| V14 | `visit_change_step` | core | core | Each change re-rostering asks visit to make, with its request key, until visit has made it (section 7.2) |

The next platform migration is V15. With the split done, core's platform migrations are core's own: each service
keeps its tables in its own `db/migration`. Until the schema split, the services share one schema, so the two events
tables are shared too: `outbox_event.source` and `consumed_message.consumer` keep each service's rows apart.

## 7. What the move changes: commands visit and core share

In core, a command that touched visits and core's records ran in one transaction. Across two services it cannot,
so each command that reaches the other side says what happens when a call fails half-way: visit's incidents
(7.1) and re-rostering's changes to visits (7.2).

### 7.1 visit asks core to raise an incident

Three of visit's commands ask core to raise an incident inside visit's own transaction: a caregiver's report
(CG04: the incident, then the visit's pause and the command's receipt), the missed check-in scan (SYS03: the
incident, then the scan's ledger entry), and an elder's dispute (EL01: the confirmation, then the incident). In
core they shared one transaction, so a failure anywhere undid the incident too. Now core commits the incident when
visit asks, and if visit's transaction then fails, visit's facts roll back while core keeps the incident.

So that a retry does not raise a second incident, each request carries a key, and core raises one incident per
key (`IncidentRequests`, table `incident_request`, platform migration V8): the key and the incident commit
together, and a request whose key core already has gets that incident back. The keys:

| Command | Key | A retry is |
|---|---|---|
| CG04 | `caregiver-report:{actor}:{clientRequestId}` | the caregiver sending the same report again, which their app does with the same request id |
| SYS03 | `missed-check-in:{visitId}:{dueAt}` | the next scan finding the visit still without its ledger entry |
| EL01 | `service-dispute:{visitId}` | the elder answering again; a visit is answered once |

A request without a key raises an incident every time. visit's tests show both sides: `CaregiverVisitExecutionWorkflowIT`
(a failed receipt, then the caregiver's retry) and `MissedCheckInWorkflowIT` (a failed ledger entry, then the next
scan; a failed visit update, which comes before visit asks core, leaves nothing behind). The same calls also run in
a transaction in core now: the missed check-in gateway requires one, and the internal controller called it
without.

### 7.2 core asks visit to change a visit: the re-rostering saga (UC-MG04)

Re-rostering decides in core's database and changes visits in visit's: it hands a visit to another caregiver,
leaves it uncovered, calls it off or moves it (`reassign`, `markUncovered`, `callOff`, `moveTo`), several times in
one re-roster. In core that was one transaction. Across the two services a call can fail half-way: visit cannot be
reached, visit makes the change and its answer is lost (a read timeout), or visit refuses (the visit has started,
been called off or moved). Re-rostering is therefore a saga, with core as the orchestrator
(`rostering/application/VisitChangeSaga`):

| Part | Where | What | When it fails |
|---|---|---|---|
| Decide (compensable) | core, one transaction | Who takes the visit, the search behind it, the roster change created or locked, and a step for each change visit is to make, with a request key (`visit_change_step`, platform V14). Nothing in it says the change is made | Nothing to undo |
| Make the change (pivot) | visit, its own transaction | The change, sent after the decision has committed, outside any transaction of core's, with the header `Idempotency-Key`. visit makes a change once per key (`visit_request`, visit's V2): the same key gets the first answer again, a move's new visit included; a refusal is not remembered | 409 or 404: compensate. No connection, a timeout, a 5xx, a 403: send again, with the same key |
| Settle (retriable) | core, one transaction | The roster change gets its outcome, everybody is told, a default plan is audited; a move is offered to the family again at its new time | Retried until done: only core's data takes part |

**Steps.** A step is PENDING until visit answers, then DONE (made and settled), REJECTED (refused) or, when the
retries run out, ABANDONED. The step moves on with one conditional update, so when the request and the sweep finish
the same step together, only one of them settles it; visit has made the change once either way.

**The request still answers as it did.** The manager's re-roster, the family's answer, the manager's own pick, the
default plan and a cover carrying on (`LeaveCoverService`) each decide, commit, and make their changes before they
return, so when visit is well every screen and API answer is what it was before the saga. A change visit cannot
be reached for is left to the sweep, with the changes after it in the same request.

**Nobody is told before visit has made the change.** Notifications to the family, the caregivers and the elder,
the roster change's outcome, the incident of a visit nobody can cover and the default plan's audit entry are all
in the settling part.

**Semantic lock.** While a step is PENDING or ABANDONED it holds its visit: the family's answer and the manager's
pick are refused with 409 `CHANGE_IN_PROGRESS`, the default plan's sweep skips the visit, a re-roster and a cover
carrying on leave it alone, and the absence's coverage cannot be confirmed. The family's own choice needs no lock of
this kind and no compensation: the family is asked first and the visit changed after (`AWAITING_FAMILY` holds the
visit while they decide), so a family that says no has changed nothing.

**Retries.** The sweep (`VisitChangeStepScanService`, every minute, ShedLock `rostering.visit-change-steps`, with
the default plan's sweep settings) sends a step again after 1, 2, 5, 10 and 20 minutes (`VisitChangeRetries`), so
a visit service that is down for good reaches a manager in under forty minutes. It gives up earlier when the next
try would come within an hour of the visit.

| Outcome | Step | Roster change | Who hears |
|---|---|---|---|
| visit made it | DONE | Settled as decided | Everybody the use case tells |
| visit refused (409, 404) | REJECTED, with visit's reason; not tried again | As it was, and free for the next decision | The person who decided: as the answer to their request (409 or 404, as before), or, if the refusal comes from a retry, as a `ROSTER_CHANGE_NOT_MADE` notification |
| retries ran out | ABANDONED, still holding the visit | As it was | A manager, through an incident on the visit (`raiseForUnrosteredLeaveVisit`, routed like any incident). Raising one asks visit who last visited the elder, so while visit is still down the sweep raises it once visit answers. The absence cannot be confirmed until a manager re-rosters it, which sends the step again with its key |

**Known limits.**
- A retry made minutes later carries the decision made then: the caregiver chosen may no longer be free. The
  decision is not taken again; a manager can still hand-pick another.
- The screens do not show a step yet. A visit whose first change waits for visit (a visit left uncovered, a cover
  carrying on) has no roster change until it is made, so the absence screen counts it among the visits not yet
  re-rostered; re-rostering leaves it alone.
- The decision's reads of visit (the visit, the bookings behind the search) still run inside the deciding
  transaction; only the changes are outside.
- Laying out a care plan's visits (`VisitApi.schedule`) and a manager's single cover (UC-MG03, `VisitCoverService`)
  still call visit once, without a key or a saga.

`VisitChangeSagaIT` runs each failure against a run without it - the same people, visits, absence, decisions and
clock - and compares the end states once the sweep has caught up; `VisitApiIT` (visit) shows a key making a change
once against MySQL.

## 8. Which service takes which public path

Every public path is under `/api`. These go to visit; everything else under `/api` goes to core, except the paths
report and notification take (their own sections of [building-a-service.md](building-a-service.md)). Two of core's
paths sit under `/api/caregivers` beside visit's, so the rules are listed in the order they must be tried: the
first that matches wins. The front end's development server routes the same way (`frontend/vite.config.ts`).

| Order | Path | Match | Service |
|---|---|---|---|
| 1 | `/api/caregivers/me/absences` | prefix | core |
| 2 | `/api/caregivers/{id}/spot-checks` | exact, `{id}` a number | core |
| 3 | `/api/caregivers/me` | exact | visit |
| 4 | `/api/caregivers/me/schedule` | exact | visit |
| 5 | `/api/caregivers/me/incidents` | prefix | visit |
| 6 | `/api/caregivers/{id}`, `/api/caregivers/{id}/credentials` | exact, `{id}` a number | visit |
| 7 | `/api/visits` | prefix | visit |
| 8 | `/api/elders/me/visits` | prefix | visit |

A caregiver's own incident reports moved with visit to `/api/caregivers/me/incidents` (they were `POST /api/incidents`
and `GET /api/caregivers/me/incidents`), so that `/api/incidents`, the managers' and families' incidents, stays
core's whole.

## 9. The schema split: a database per service

The services keep one MySQL server, as the cloud keeps one RDS instance, and each has a database of its own on it:
`carelink` (core), `carelink_visit`, `carelink_report` and `carelink_notification`. Each service connects with an
account that can reach its own database only, so a statement on another service's table fails.

| Service | Database | Status |
|---|---|---|
| core | `carelink` | Keeps core's tables. Each service's tables leave it in a platform migration once that service has its own |
| notification | `carelink_notification` | Done: its own migrations build it; core dropped its tables (V11) |
| visit | `carelink_visit` | Done: its own migrations build it, with its own `audit_log` and `shedlock`; core dropped its tables (V12) |
| report | `carelink_report` | Done: its own migrations build it, with the facts it keeps from events, the value-added catalogue, its own `audit_log` and `shedlock`; core dropped its tables (V13) |

**What a service with its own database has.**
- **Its own migrations** in `src/main/resources/db/migration`, starting at V1 with its tables as they stood in the
  shared schema when they moved. It enables Flyway; libs/shared's `PlatformMigrationsAutoConfiguration` runs its
  migrations and then the platform's (`db/platform` in every jar on the classpath, for example the events tables
  libs/events ships), as core does.
- **Its own `audit_log`**, since the shared access audit writes it in every service, and its own `shedlock` when it
  has scheduled jobs. Both go in its own migrations, not in a platform migration of its own.
- **Its connection settings**, `DB_URL`, `DB_USER` and `DB_PASSWORD`, pointing at its own database.
- **A check that its code names only its own tables** (`architecture/OwnTablesTest`, with `OwnTables` from
  libs/test-support): SQL in its strings and text blocks, and JPA's `@Table`.

**Its tests.** `OwnDatabaseForTests` (libs/test-support) builds the tests' database in three steps:
1. core's migrations, with history tables of their own, as core's database stands. The core stand-in answers from
   these tables, and the tests lay out accounts, elders and bindings in them as before.
2. Every table that belongs to a service (`ServiceTables`) is dropped from that copy, so a statement on another
   service's table fails in the tests as it would in a real run.
3. The service's own migrations and then the platform's, exactly as they build its database in a real run.

**Locally**, `mysql-setup` in `docker-compose.yml` runs `scripts/mysql/service-databases.sh` on every start: it
creates each service's database and account if they are missing, on a volume from before the split too.

**Data.** The split starts each service's database empty. Rows a service kept in the shared schema are not carried
over, and core drops the moved tables.

**Demonstration data** (core's `db/demo`) still lays out whole scenarios, each in one file, but every row goes to the
database of the service it belongs to:
- visit's and report's tables are named with their databases (`carelink_visit.visit`, `carelink_report.report`), so
  load a file with an account that can write every database on the server.
- The one family message is an event in core's outbox (`manager-absences-and-spot-checks.sql`), which notification
  keeps when core's relay publishes it.
- Rows loaded straight into the databases send no events, so report would not hear of them. Load `report-facts.sql`
  last: it copies the facts into report's tables the way the first deployment did (V10).

core's tests that load demonstration data build visit's database beside their own (`VisitDatabaseForDemoData`).

**What the cloud environment needs** for each service with a database: the database on the RDS instance, an account
whose privileges cover that database only, and the service's `DB_URL`, `DB_USER` and `DB_PASSWORD` in the Secret
its chart reads (`envFromSecret`).

