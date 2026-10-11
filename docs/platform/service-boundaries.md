# Service boundaries

What has to change when visit, report and notification move out of core: which schema owns each table, which
foreign keys cross a schema, what core offers the other services in place of today's in-process calls, and which
SQL still reads another service's tables.

Generated from the code at `c124099` (Flyway migrations V1–V25, 50 tables). Owner: core and shared parts. Each
service owner refines their own section when they split the service.

**Where the move stands.** visit, report and notification run as services of their own. visit and report reach
core only through ports of their own (`visit/application`, `report/application`), whose adapters call `CoreApi`
(`…/infrastructure/core`); report reaches visit through `VisitApi`. Rostering and incident, which stay in core,
reach visit through ports of theirs whose adapters call `VisitApi`. The calls in sections 3 and 4 are all done. The
SQL and writes in section 5 still run until they are replaced, which comes before the schema split. Section 7 lists
what the move changes for the commands visit and core take part in together, and section 8 the paths the Ingress
sends to each service.

---

## 1. Which schema owns each table

| Schema | Tables | By module |
|---|---|---|
| `core` | 31 | **careplan**: `care_plan`, `care_plan_node`, `care_plan_node_visit`, `care_plan_required_credential`<br>**identity**: `app_user`, `user_role`<br>**incident**: `family_alert_delivery`, `family_alert_event`, `family_alert_window`, `incident`, `incident_acknowledgement`, `incident_log`, `spot_check`<br>**profile**: `care_service_application`, `caregiver`, `credential`, `credential_type`, `elder`, `elder_family_binding`, `elder_primary_caregiver`, `family_member`, `intake_application`<br>**rostering**: `absence_report`, `caregiver_availability`, `caregiver_preference`, `roster_change`, `rostering_candidate`, `rostering_candidate_check`, `rostering_constraint`, `rostering_run`<br>**shared**: `audit_log` |
| `visit` | 11 | `caregiver_command_receipt`, `elder_confirmation`, `visit`, `visit_assignment`, `visit_check_in_record`, `visit_evidence`, `visit_health_record`, `visit_missed_check_in_trigger`, `visit_state_transition`, `visit_task`, `vital_sign` |
| `report` | 6 | `caregiver_review`, `report`, `report_amendment`, `report_basis`, `value_added_service`, `value_added_service_request` |
| `notification` | 2 | `notification`, `notification_subscription` |

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
| SQL on `elder`, `elder_primary_caregiver`, `caregiver`, `care_plan` in `ReportFactsJdbcSource` (the elder a report describes) | report | `GET /internal/v1/elders/{elderId}/report-profile` → the profile, the primary caregiver and the latest published plan, or 404 |
| SQL on `absence_report` in `JdbcValueAddedVisitAssignment` (is the caregiver away that day) | report | `GET /internal/v1/caregivers/{caregiverId}/on-leave?day=` → `{ onLeave }` |
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
| `VisitReassignment.reassign`, `markUncovered`, `callOff` | rostering; report (`callOff`) | `POST /internal/v1/visits/{visitId}/reassignment`, `…/uncovered`, `…/call-off` → 204 |
| `VisitReassignment.moveTo` | rostering | `POST /internal/v1/visits/{visitId}/move` → `{ visitId }` of the new visit |
| `VisitScheduling.schedule` | rostering | `POST /internal/v1/visits/schedule` → `{ created, covered }` |
| `VisitScheduling.cancelUntouchedFrom` | rostering | `POST /internal/v1/visits/cancel-untouched` → `{ count }` |
| `VisitScheduling.findUncoveredStarted`, `markUncoveredAsException` | rostering | `GET /internal/v1/visits/uncovered-started?since=&now=`; `POST /internal/v1/visits/{visitId}/uncovered-exception` → `{ marked }` |
| `UpcomingAssignments.unstartedBetween` | rostering | `GET /internal/v1/visits/upcoming-assignments?from=&until=`. Done: rostering's `BookedVisits` port has both adapters, the worked example |
| `StandaloneVisits.schedule`, `find` | report | `POST /internal/v1/visits/standalone` → `{ visitId }`; `GET /internal/v1/visits/{visitId}/state`, or 404 |
| `VisitScheduleQuery.caregiverIdsForElder`, `hasAssignedVisit` | report | `GET /internal/v1/visits/caregivers-of-elder?elderId=`; `GET /internal/v1/visits/assigned?elderIds=&caregiverId=` → `{ assigned }` |
| SQL on `visit` in `JdbcSpotCheckLookups` | incident | `GET /internal/v1/visits/{visitId}`; `GET /internal/v1/visits/upcoming?elderId=&from=&until=` |
| SQL on `visit` in `JdbcValueAddedVisitAssignment` (does the caregiver have a visit at that time) | report | `GET /internal/v1/visits/caregiver-busy?caregiverId=&from=&until=` → `{ busy }`. Its times are bound the way JPA writes them, which the SQL it replaces did not do |
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
stayed as it was; the ninth listens for careplan's `CarePlanPublished` after the publish commits, and writes its
events in a transaction of its own. `NotificationRequested.inApp` cuts titles and bodies to what the inbox keeps.

| Module | Class |
|---|---|
| profile | `NotificationRequestedCredentialAlert` (the worked example), `CarePlanPublishedFamilyNotifier` |
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
| report `ReportFactsJdbcSource` | `care_plan`, `caregiver`, `elder`, `elder_confirmation`, `elder_primary_caregiver`, `incident`, `incident_log`, `roster_change`, `spot_check`, `visit`, `visit_evidence`, `visit_task`, `vital_sign` | A read model in report's schema, filled from events; the elder through `CoreApi.elderReportProfile` |
| report `JdbcValueAddedVisitAssignment` | `absence_report`, `visit` | `CoreApi.onLeave` and `VisitApi.caregiverBusy` |
| report `NotificationRequestedValueAddedManagerAlert` | none now; it reads `app_user`, `user_role`, `elder` and `caregiver` through `CoreApi` | Done |
| report `NotificationRequestedValueAddedNotifier` | none now; the caregiver's account through `CoreApi.caregiverAccount`, the names through `CoreApi` | Done |
| notification `JdbcNotificationInbox` | none now | Done: each message keeps the elder its event names (`notification.elder_id`), and the family rule takes the elders `CoreApi.readableElders` answers |
| notification `JdbcRecipientDirectory` | none now | Done: `CoreRecipientDirectory` asks `CoreApi.findAccount` |
| incident `JdbcSpotCheckLookups` | none now | Done: the time of the last ask is on the spot check, and the visits are calls to visit (section 4) |

visit's `CaregiverCommandStoreAdapter` writes `audit_log` through the shared audit classes. After the split, each
service keeps its own `audit_log` in its own schema.

The schema split comes after these reads are gone. Until then the four parts share one schema.

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

The next platform migration is V8. Until the schema split, the services share one schema, so the two events
tables are shared too: `outbox_event.source` and `consumed_message.consumer` keep each service's rows apart.

## 7. What the move changes: commands visit and core share

Three of visit's commands ask core to raise an incident inside visit's own transaction: a caregiver's report
(CG04: the incident, then the visit's pause and the command's receipt), the missed check-in scan (SYS03: the
incident, then the scan's ledger entry), and an elder's dispute (EL01: the confirmation, then the incident). In
core they shared one transaction, so a failure anywhere undid the incident too. Now core commits the incident when
visit asks; if visit's transaction then fails, visit's facts roll back and core keeps the incident, and a retry
raises a second one. For CG04 and SYS03 a failed write after the call is enough; for EL01 only a failed commit.
Nothing is lost, but staff may see the same report twice.

visit's tests show this as it is: `CaregiverVisitExecutionWorkflowIT` (a failed receipt) and
`MissedCheckInWorkflowIT` (a failed ledger entry; a failed visit update, which comes before visit asks core, leaves
nothing behind). The way out is the same for all three: visit stops calling core inside its transaction, records
the fact and an event in one transaction instead, and core raises the incident from the event, once per event
(`consumed_message`); visit learns the incident's id from core's `IncidentRaised`. The event catalogue decided the
other way for the missed check-in (`VisitMissed`, section 3.1), because visit stores the incident's id; this
reopens that decision, to be settled with visit's events before the schema split.

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
