# Service boundaries

What has to change when visit, report and notification move out of core: which schema owns each table, which
foreign keys cross a schema, what core offers the other services in place of today's in-process calls, and which
SQL still reads another service's tables.

Generated from the code at `c124099` (Flyway migrations V1–V25, 50 tables). Owner: core and shared parts. Each
service owner refines their own section when they split the service.

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

17 of the 65 foreign keys cross a schema. Each is dropped at the split. The column stays and keeps the id; the
service that owns the column checks the reference in code before writing. For example, visit asks core whether
the elder exists before it creates a visit.

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

Today visit and report call core's Java types directly. After the split, each call becomes one of the replacements
below. Internal endpoints live under `/internal/v1/`. The Ingress routes only the public paths (`/api/**`), never
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
| `MissedCheckInPauseEvidence.isSoleIncident(elderId, visitId, incidentId)` | visit (`MissedCheckInResumeService`) | **Not in `CoreApi` yet.** A read: is this incident the visit's only one, raised by the missed check-in scan? It becomes an endpoint under `/internal/v1/incidents/` before visit moves out |
| `VisitCover.options(visitId)`, `cover(…)` | report (2) | `GET /internal/v1/visits/{visitId}/cover-options`; `POST /internal/v1/visits/{visitId}/cover`. Rostering stays in core and calls visit in turn |

The caller keeps the port it has today and swaps the adapter. For example, visit keeps its own `FamilyAccess`
interface, and the implementation becomes a call through `CoreApi`, which has connect and read timeouts. The
business code that calls the port does not change. The library does not retry: a read can simply be repeated, and
a `POST` is repeated only by a caller that knows it is safe (the missed check-in scan tries again on its next run).

## 4. What core needs from visit

This is input for the visit split. The visit owner defines visit's internal API.

| Call today | Called from |
|---|---|
| `VisitScheduling.schedule`, `cancelUntouchedFrom`, `findUncoveredStarted`, `markUncoveredAsException` | rostering |
| `VisitReassignment.find`, `reassign`, `moveTo`, `callOff`, `markUncovered`, `bookingsBetween`, `finishedVisitsWith`, `unstartedFor` | rostering (11 classes) |
| `UpcomingAssignments.unstartedBetween` | rostering |
| SQL on `visit` | incident: `JdbcSpotCheckLookups`, and the "latest caregiver of this elder" query in `NotificationTableAlert` |

Some of these calls can become events instead (`VisitScheduled`, `AbsenceReported`), as the event catalogue
decides.

## 5. SQL and writes that reach another service's tables

### 5.1 Writes to the notification table → `NotificationRequested` event

Nine classes insert into `notification` directly. Eight implement a port, so only the adapter changes, and
the business code that raises the alert stays as it is. The ninth, `CarePlanPublishedFamilyNotifier`, listens
for careplan's `CarePlanPublished` event and changes in the same way.

| Module | Classes |
|---|---|
| incident | `NotificationTableAlert`, `NotificationTableSpotCheckAlert`, `JdbcFamilyAlertDeliveryStore` |
| profile | `NotificationTableCredentialAlert`, `CarePlanPublishedFamilyNotifier` |
| rostering | `NotificationTableAbsenceAlert`, `NotificationTableRosterAlert` |
| report | `NotificationTableValueAddedManagerAlert`, `NotificationTableValueAddedNotifier` |

### 5.2 Reads of another service's tables

| Class | Reads from other schemas | Direction of the fix |
|---|---|---|
| report `ReportFactsJdbcSource` | `care_plan`, `caregiver`, `elder`, `elder_confirmation`, `elder_primary_caregiver`, `incident`, `incident_log`, `roster_change`, `spot_check`, `visit`, `visit_evidence`, `visit_task`, `vital_sign` | A read model in report's schema, filled from events |
| report `JdbcValueAddedVisitAssignment` | `absence_report`, `visit` | Calls to core and visit |
| report `NotificationTableValueAddedManagerAlert` | `app_user`, `user_role` | Recipients go in the event |
| report `NotificationTableValueAddedNotifier` | `caregiver` | Recipients go in the event |
| notification `JdbcNotificationInbox` | `care_plan`, `elder_family_binding`, `family_member`, `incident`, `roster_change`, `spot_check`, `value_added_service_request` | The event carries what the inbox shows, stored with the notification |
| notification `JdbcRecipientDirectory` | `app_user`, `user_role` | Recipients go in the event |
| incident `JdbcSpotCheckLookups` | `visit`, `notification` | Calls to visit; the reminder time kept in core |

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

The next platform migration is V3. Until the schema split, the services share one schema, so the two events
tables are shared too: `outbox_event.source` and `consumed_message.consumer` keep each service's rows apart.
