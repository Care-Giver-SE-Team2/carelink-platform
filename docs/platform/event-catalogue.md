# Event catalogue

This catalogue lists the events the services publish to each other: what each event carries and who handles it.

Version 1 was worked out from what report read from other modules' tables (`ReportFactsJdbcSource` and
three smaller classes, see [service-boundaries.md](service-boundaries.md), section 5.2). It also covers the two
events that notification and the report worker need.

A change goes through a pull request, reviewed by the owner of the service that publishes the event and the owner
of each service that handles it.
- **Adding a field** is compatible.
- **Renaming or removing a field, or changing what it means,** needs a new event name.

## 1. Envelope and delivery

Every event travels in the envelope that `libs/events` writes:

| Field | What |
|---|---|
| `id` | A UUID. A consumer handles each id once (`consumed_message`). |
| `type` | The event's name, as in section 3. |
| `source` | The service that published it: `core`, `visit`, `report` or `notification`. |
| `occurredAt` | When the event was published, as an instant in UTC: `"2026-10-12T01:00:03.120Z"`. |
| `sequence` | The number of the event's outbox row. It increases within a source. Of two changes to one record, the later one has the larger number, as long as the publisher changes the record (which locks it) before it publishes, in the same transaction. A handler reads it as `EventMetadata.sequence()`. It is null on a message that did not come from an outbox, such as `ReportRequested`. |
| `payload` | The fields listed in section 3. |

- **Delivery.** At least once, in no particular order.
  - A consumer skips any `id` it has already handled.
  - It also drops an update that is older than what it already holds, by the ordering field each event names: `version`, `logId` or `sequence`.
- **Routing.** There is one SNS topic, `carelink-events`.
  - Each service's queue subscribes with a filter policy on the message attribute `type`, listing the events that service handles. So a service never receives events it has no use for.
  - Locally, `scripts/localstack/events.sh` subscribes notification's and report's queues with their filters. core and visit handle nothing yet, so their queues have no subscription. A service gets its subscription, there and in the Terraform, with its first handler.
- **Failures.** After five failed receives, a message moves to the service's dead-letter queue.

## 2. Payload rules

- **Times.**
  - A business time (a visit's start, a check-in, a reading) is Singapore wall-clock time with its offset, to the second: `"2026-10-12T09:00:00+08:00"`. Produce it with `SingaporeTime.of(localDateTime)` from `libs/event-types`. It rounds to the second the way the database rounds a `DATETIME`, so the event says what the database keeps. A handler turns it back with `SingaporeTime.local(...)`, whatever offset its JSON reader hands it.
  - Never send the value as the database stores it, and never send a time without an offset.
  - A date is an ISO date: `"2026-10-12"`.
- **Numbers.** Ids are JSON numbers. Readings and amounts are decimal strings (`"37.50"`), read into `BigDecimal`.
- **Enums** are sent as their names (`"COMPLETED"`).
- **Facts that belong to a visit.** Every event about a visit carries `elderId` and the visit's `scheduledStart`. So does every event about something that belongs to a visit: a task, a set of readings, the elder's confirmation. In those events the field is called `visitScheduledStart`. A consumer can then file the event under the right elder and week without waiting for the visit's other events.
- **State, not commands.** An event says what happened and carries the record's state after it. The consumer decides what to do with it.
- **Records.** Each event's payload is a record in `libs/event-types`, with the event's name as `TYPE`. The publisher builds it and every handler reads the same record.
- **Sensitive events.** `VitalsRecorded`, `IncidentRaised` and `IncidentUpdated` carry health and care details: readings, an incident's description, a manager's notes.
  - Only the queues of services that need these events subscribe to them.
  - No service logs a payload. Log the type and the id instead.
- **Transactions.** Publish in the transaction that changes the data; `Events.publish` throws without one.

## 3. Events

### 3.1 Summary

Each event is listed with its publisher, its trigger, its handler, and when it gets built. The status values mean:

| Status | Meaning |
|---|---|
| **published** | Published today. Its record is in `libs/event-types`. |
| **with the move** | Built when that module moves out of core, by its owner. |
| **draft** | Fields still to be agreed. |
| **reserved** | No code produces this fact yet. |

**Visit's events.** visit publishes these, each in the transaction of the change (`VisitEvents`, adapter
`OutboxVisitEvents` in `visit/infrastructure/events`). One change to a visit is one event, carrying the visit's
version after the change; the version also moves on changes that publish nothing, such as a task result or a
missed check-in, so a consumer sees gaps, never a repeat.

| Event | Published by | When | Handled by | Status |
|---|---|---|---|---|
| `VisitScheduled` | `VisitSchedulingService.schedule`, `StandaloneVisitsService.schedule`, `VisitReassignmentService.moveTo` | A visit is created | report | published |
| `VisitCaregiverChanged` | `VisitSchedulingService.schedule` (when it covers an existing visit), `VisitReassignmentService.reassign` | A caregiver takes the visit over | report | published |
| `VisitCancelled` | `VisitSchedulingService.cancelUntouchedFrom`, `VisitReassignmentService.callOff`, `moveTo` (the visit it replaces) | The visit becomes CANCELLED | report | published |
| `VisitCheckedIn` | `CaregiverVisitExecutionService.checkIn` | A caregiver checks in, including a check-in that resumes after a missed one | report | published |
| `VisitCompleted` | `CaregiverVisitExecutionService.checkOut` | The visit becomes COMPLETED | report: the weekly report and value-added settlement | published |
| `VisitExceptionRaised` | `CaregiverIncidentReportingService.report`, `VisitSchedulingService.markUncoveredAsException`, `VisitReassignmentService.markUncovered` | The visit becomes EXCEPTION | report | published |
| `VisitTaskRecorded` | `CaregiverVisitExecutionService.taskResult` | A task's result is recorded | report | published |
| `VitalsRecorded` | `CaregiverHealthService.record` | A set of readings is recorded | report | published |
| `ElderConfirmationSubmitted` | `VisitService.submitElderConfirmation` | The elder confirms or disputes a visit | report | published |
| `VisitClosed` | `VisitService.submitElderConfirmation` (CONFIRMED); nothing sets AUTO_CLOSED yet | The visit becomes VERIFIED or AUTO_CLOSED | report | published |
| `VisitEvidenceUpdated` | `CaregiverEvidenceService.upload` | A private evidence upload commits a new count snapshot | report | published |

**The state a visit event leaves.** Each event says what the visit is after it, so a consumer that keeps visits
applies an event only when its `version` is higher than the one it holds, and needs nothing else:

| Event | Status after it | Caregiver after it |
|---|---|---|
| `VisitScheduled` | SCHEDULED | `caregiverId` (may be null) |
| `VisitCaregiverChanged` | SCHEDULED | `caregiverId` |
| `VisitCancelled` | CANCELLED | `caregiverId` (unchanged by the cancellation) |
| `VisitCheckedIn` | IN_PROGRESS | `caregiverId` |
| `VisitCompleted` | COMPLETED | `caregiverId` |
| `VisitExceptionRaised` | EXCEPTION | `caregiverId` (null when nobody covers it) |
| `VisitClosed` | `status`: VERIFIED or AUTO_CLOSED | `caregiverId` |

What never changes after `VisitScheduled` - the service, the plan and its task, the end time - comes only with
it. When an update arrives first, keep its state and fill those fields in when `VisitScheduled` arrives, whatever
its version.

**Core's events.**

| Event | Published by | When | Handled by | Status |
|---|---|---|---|---|
| `IncidentRaised` | incident: the eight `IncidentService` methods that create an incident | An incident is created | report | published |
| `IncidentUpdated` | incident: every entry added to an incident's timeline. All entries go through `IncidentLogRepository.save`, from `IncidentService` and `EscalationService` | A timeline entry is added | report | published |
| `SpotCheckUpdated` | incident: `SpotCheckService.request`, `decide`, `moveTo`, `conclude`, `reportNoShow`, `respond`, `withdraw` | A spot check changes | report | published |
| `RosterChangeUpdated` | rostering: `AbsenceReRosteringService.reroster`, `decide`, `applyDefaultIfStillDue`, `assignByManager`; `RosterChangeScanService.sweep`; `LeaveCoverService.giveToCover` | A roster change is created or settled | report | published |

Each of core's events is published where its record is saved, in the repository adapter's `save` and in the same
transaction, so every method listed publishes it.

**Requests.**

| Event | Published by | When | Handled by | Status |
|---|---|---|---|---|
| `NotificationRequested` | core: the credential expiry alert, incident's staff, spot check and family alerts, rostering's absence and roster change messages, the care plan notice; report: the value-added messages. Each sends one event per recipient | A message should reach someone | notification | published |
| `ReportRequested` | EventBridge Scheduler, straight into report's queue | Once a week | the report worker | with the report move |

**The Open Platform's events.** Third-party providers take value-added service jobs through the Open Platform
([open-platform.yaml](../api/open-platform.yaml)). notification keeps the providers and report keeps the jobs
(`docs/section5/decisions.md`, D1 to D3); each tells the other what changed.

| Event | Published by | When | Handled by | Status |
|---|---|---|---|---|
| `ProviderUpdated` | notification (openplatform): approving, suspending or reinstating a provider, and rotating its client secret | An approved provider is added or changes | report: whom it can dispatch to, and whose token is whose (not built yet) | published |
| `ProviderJobUpdated` | report: dispatching a request to a provider, the provider's answers through the job API, and calling the job off | A provider job is created or changes | notification: the provider's webhooks | draft |

**Not built.**

| Event | Why not |
|---|---|
| `VisitMissed` | report learns about a missed check-in from `IncidentRaised`. visit keeps raising the incident through `CoreApi.raiseMissedCheckIn`, because it stores the incident's id. |
| `AbsenceReported` | Value-added dispatch asks core directly (section 4). Re-rostering stays in core and calls visit through `VisitApi`. Revisit this with the reassignment saga. |
| `IncidentEscalated` | `IncidentUpdated` covers it, with action `ESCALATED` or `CHAIN_EXHAUSTED`. |

### 3.2 Payloads

**`VisitScheduled`**

```json
{ "visitId": 812, "elderId": 101, "caregiverId": 7, "carePlanId": 21, "carePlanNodeId": 40,
  "serviceType": "BATHING", "scheduledStart": "2026-10-12T09:00:00+08:00",
  "scheduledEnd": "2026-10-12T10:00:00+08:00", "origin": "PLAN", "movedFromVisitId": null, "version": 0 }
```

- `caregiverId` is null while nobody is on the visit.
- `carePlanId` and `carePlanNodeId` are null for a standalone visit.
- `scheduledEnd` may be null.
- `origin` is `PLAN`, `STANDALONE` or `MOVED`. `movedFromVisitId` is set when `origin` is `MOVED`.
- Ordering: `version`, as in every visit event.

**`VisitCaregiverChanged`**

```json
{ "visitId": 812, "elderId": 101, "scheduledStart": "2026-10-12T09:00:00+08:00", "caregiverId": 9,
  "previousCaregiverId": 7, "absenceId": 33, "version": 1, "changedAt": "2026-10-11T15:20:00+08:00" }
```

- After this event the visit is SCHEDULED with `caregiverId`. A visit an absence leaves with nobody is
  `VisitExceptionRaised`, cause `ABSENCE_UNCOVERED`.
- `previousCaregiverId` is null when the visit had nobody. `absenceId` is set when an absence caused the change.
- `changedAt` is when visit published the change.

**`VisitCancelled`**

```json
{ "visitId": 812, "elderId": 101, "scheduledStart": "2026-10-12T09:00:00+08:00", "caregiverId": 9,
  "reason": "ABSENCE", "absenceId": 33, "note": "Family skipped the visit", "version": 2,
  "cancelledAt": "2026-10-11T18:02:00+08:00" }
```

`reason` is one of:
- `PLAN_CHANGED`: `cancelUntouchedFrom`.
- `ABSENCE`: `callOff` or `moveTo` with an absence.
- `CALLED_OFF`: `callOff` or `moveTo` without an absence, for example when an extra service is withdrawn.

`note` is the reason text given to `callOff` or `moveTo`. A moved visit's new time is a new visit:
`VisitScheduled` with `origin` `MOVED` and `movedFromVisitId`.

**`VisitCheckedIn`**

```json
{ "visitId": 812, "elderId": 101, "scheduledStart": "2026-10-12T09:00:00+08:00", "caregiverId": 9,
  "checkedInAt": "2026-10-12T09:03:00+08:00", "resumedAfterMissedCheckIn": false, "version": 3 }
```

After this event the visit is IN_PROGRESS.

**`VisitCompleted`**

```json
{ "visitId": 812, "elderId": 101, "scheduledStart": "2026-10-12T09:00:00+08:00", "caregiverId": 9,
  "checkedInAt": "2026-10-12T09:03:00+08:00", "checkedOutAt": "2026-10-12T10:01:00+08:00", "version": 4 }
```

**`VisitExceptionRaised`**

```json
{ "visitId": 812, "elderId": 101, "scheduledStart": "2026-10-12T09:00:00+08:00", "caregiverId": 9,
  "cause": "CAREGIVER_REPORT", "absenceId": null, "incidentId": 601, "checkedIn": true, "version": 5 }
```

- `cause` is `CAREGIVER_REPORT`, `UNCOVERED_AT_START` or `ABSENCE_UNCOVERED`.
- `caregiverId` is null for `UNCOVERED_AT_START` and `ABSENCE_UNCOVERED`: nobody covers the visit.
- `absenceId` is set for `ABSENCE_UNCOVERED`.
- `incidentId` is the caregiver's report for `CAREGIVER_REPORT`, and null otherwise: core raises its own alert
  for an uncovered visit.
- A report on a visit already in EXCEPTION, or already completed, changes nothing and publishes nothing.

**`VisitTaskRecorded`**

```json
{ "taskId": 3051, "visitId": 812, "elderId": 101, "visitScheduledStart": "2026-10-12T09:00:00+08:00",
  "name": "Bathing", "status": "DONE", "outcome": null, "caregiverNote": "Enjoyed the bath",
  "recordedAt": "2026-10-12T09:40:00+08:00" }
```

`status` is `DONE`, `SKIPPED` or `REFUSED`. Ordering: `sequence`.

**`VitalsRecorded`** (sensitive)

```json
{ "healthRecordId": 77, "visitId": 812, "elderId": 101, "visitScheduledStart": "2026-10-12T09:00:00+08:00",
  "recordedAt": "2026-10-12T09:20:00+08:00", "healthFlag": "ATTENTION", "healthNote": "Slightly warm",
  "readings": [ { "metric": "temperature", "value": "37.80", "unit": "°C", "outOfRange": false } ] }
```

- `metric` is `systolic`, `diastolic`, `pulse` or `temperature`.
- `value` has two decimals, as `vital_sign` keeps it.
- `outOfRange` is the stored flag. See section 6.
- `healthFlag` is the caregiver's own judgement: `NO_CONCERN`, `ATTENTION` or `MEDICAL_REVIEW`.

**`ElderConfirmationSubmitted`**

```json
{ "visitId": 812, "elderId": 101, "visitScheduledStart": "2026-10-12T09:00:00+08:00", "status": "CONFIRMED",
  "rating": 5, "comment": "Very kind", "confirmedAt": "2026-10-12T19:30:00+08:00", "incidentId": null }
```

- `status` is `CONFIRMED` or `DISPUTED`.
- `rating` (1 to 5) may be null.
- `incidentId` is set when the dispute raised an incident.

**`VisitClosed`**

```json
{ "visitId": 812, "elderId": 101, "scheduledStart": "2026-10-12T09:00:00+08:00", "caregiverId": 9,
  "status": "VERIFIED", "version": 5, "closedAt": "2026-10-12T19:30:00+08:00" }
```

- `status` is `VERIFIED` (the elder confirmed the visit) or `AUTO_CLOSED` (the elder never answered).
- A confirmation publishes `VisitClosed` before `ElderConfirmationSubmitted`, in the same transaction. A dispute
  publishes no `VisitClosed`: the visit stays COMPLETED.
- The check-in and check-out times are unchanged, so they are not repeated.

**`VisitEvidenceUpdated`**

```json
{ "visitId": 812, "elderId": 101, "scheduledStart": "2026-10-12T09:00:00+08:00",
  "version": 7, "evidenceCount": 2, "verifiedEvidenceCount": 0 }
```

- A committed upload publishes a count snapshot in the same transaction as its command receipt.
- Report orders this independent projection by `version`; duplicates and older snapshots are ignored.
  It does not rewind the visit's execution status or version. Older copied counters are the fallback
  until a snapshot exists. No file paths, image bytes, filenames or storage identifiers are sent.
- This producer does not add a verification command: uploaded images remain UNVERIFIED.

**`IncidentRaised`** (sensitive)

```json
{ "incidentId": 601, "elderId": 101, "visitId": 812, "source": "CAREGIVER", "category": "FALL",
  "severity": "HIGH", "status": "OPEN", "description": "…", "reportedAt": "2026-10-12T09:35:00+08:00" }
```

- `visitId` is null for an incident with no visit, such as an SOS.
- `source` is `CAREGIVER`, `ELDER_SOS`, `ELDER_SERVICE_DISPUTE` or `SYSTEM_MISSED_CHECKIN`.
- `description` is up to 2000 characters.

**`IncidentUpdated`** (sensitive)

```json
{ "incidentId": 601, "elderId": 101, "logId": 9001, "action": "CLAIMED", "actor": "lee.manager",
  "detail": null, "occurredAt": "2026-10-12T09:41:00+08:00", "status": "IN_PROGRESS", "severity": "HIGH",
  "resolvedAt": null }
```

- One event is sent per timeline entry, including the first one (`REPORTED`).
- `actor` is a username or `system`.
- `detail` holds the manager's notes. On `RESOLVED` it is the outcome code, then `::`, then the note.
- The incident's current `status`, `severity` and `resolvedAt` come along with each entry.
- Ordering: `logId`, which increases within an incident.

**`SpotCheckUpdated`**

```json
{ "spotCheckId": 41, "elderId": 101, "visitId": 812, "caregiverId": 9,
  "proposedTime": "2026-10-14T10:00:00+08:00", "approvalStatus": "APPROVED", "result": null, "outcome": null,
  "finding": null, "caregiverResponse": null, "checkedAt": null, "changedAt": "2026-10-12T20:00:00+08:00" }
```

The spot check's whole state after each change. Ordering: `sequence`.

**`RosterChangeUpdated`**

```json
{ "rosterChangeId": 55, "visitId": 812, "elderId": 101, "absenceId": 33,
  "visitStart": "2026-10-12T09:00:00+08:00", "originalCaregiverId": 7, "status": "RESOLVED",
  "outcome": "REPLACED", "decidedBy": "FAMILY", "assignedCaregiverId": 9,
  "changedAt": "2026-10-11T16:00:00+08:00" }
```

- `status` is `AWAITING_FAMILY`, `UNCOVERED` or `RESOLVED`.
- `outcome` is `REPLACED`, `RESCHEDULED`, `SKIPPED` or `WITHDRAWN`.
- `decidedBy` is `FAMILY`, `DEFAULT_PLAN` or `MANAGER`. A leave cover is `REPLACED` by `DEFAULT_PLAN`.
- Ordering: `sequence`.

**`NotificationRequested`**

```json
{ "recipientUserId": 7, "kind": "INCIDENT_RAISED", "channel": "IN_APP", "title": "Urgent care alert: HIGH",
  "body": "A FALL incident has been reported. Open the incident details.", "resourceType": "INCIDENT",
  "resourceId": 601, "elderId": 101, "requestedAt": "2026-10-12T09:35:00+08:00" }
```

- One event per recipient, as the rows are written today. The publisher works out its recipients, as it does today.
- notification keeps it as a PENDING message, created at `requestedAt`; the inbox delivers it when the recipient next asks. The notification owner may add fields.
- `elderId` lets the inbox apply the family rule without joining other services' tables. The inbox still asks core whether the reader is bound to that elder.

**`ReportRequested`**

EventBridge Scheduler puts the envelope on report's queue directly.
- Its input template fills `id` from the scheduler's execution id and `occurredAt` from the scheduled time, so each week's request has its own id.
- The payload is empty. The worker reports on the week that ended before `occurredAt`.

**`ProviderUpdated`**

```json
{ "providerId": 509, "clientId": "cl_prov_BrightHome9", "organisationName": "BrightHome Care",
  "status": "ACTIVE", "services": ["Hospital escort", "Companionship"], "version": 3,
  "changedAt": "2026-10-15T11:20:00+08:00" }
```

- Only approved providers: a pending or rejected application sends nothing.
- `status` is `ACTIVE` or `SUSPENDED`. A suspended provider is dispatched nothing, and its token is refused.
- `clientId` is the provider's OAuth2 client, which the job API's token names (`client_id`). It is set from
  approval, and changes when the provider rotates its secret, as rotating issues a new client.
- `services` are names from report's value-added catalogue.
- Ordering: `version`, which increases with every change to the provider.

**`ProviderJobUpdated`** (draft)

```json
{ "jobId": 24081, "providerId": 509, "valueAddedServiceRequestId": 812, "status": "NEW",
  "serviceName": "Hospital escort", "district": "19", "areaName": "Hougang",
  "scheduledStart": "2026-10-17T10:00:00+08:00", "durationMinutes": 180,
  "respondBy": "2026-10-16T10:00:00+08:00", "cancelReason": null, "version": 0,
  "changedAt": "2026-10-15T09:02:11+08:00" }
```

- `status` is `NEW`, `ACCEPTED`, `DECLINED`, `COMPLETED` or `CANCELLED`, as the job API has it. The job API shows
  `jobId` as `JOB-24081`.
- notification sends the provider `job.dispatched` for `NEW` and `job.cancelled` for `CANCELLED`
  ([webhooks.md](../api/webhooks.md)); it does nothing with the other states, which are the provider's own answers.
- It carries no elder's name, address or care details: notification does not need them, and the provider reads
  them through the job API.
- `respondBy` is null once the job is not `NEW`. `cancelReason` is set for `CANCELLED`.
- Ordering: `version`, which increases with every change to the job, as the webhooks carry it.

## 4. Asked when needed, not sent as events

- **Master data at generation time.**
  - Some facts rarely change and are read once per report: the elder's profile, the elder's current published care plan (version and weekly hours), the primary caregiver, and caregivers' names.
  - report asks core for them when it generates, and keeps them in the report's facts snapshot, as it does today.
  - One call answers all of it: `CoreApi.elderReportProfile`. A caregiver's name is `findCaregiverPublicProfile`.
- **"Is this caregiver free right now?"** Value-added dispatch needs the answer at that moment, so it asks rather than keeping a copy that could lag:
  - core: is the caregiver on approved leave that day? `CoreApi.onLeave`
  - visit: does the caregiver have an active visit that overlaps this time? `VisitApi.caregiverBusy`

## 5. What report keeps

- **Tables.** report keeps one table per kind of fact (platform migration V9: `report_visit`, `report_visit_task`, `report_vital_sign`, `report_elder_confirmation`, `report_incident`, `report_incident_log`, `report_spot_check`, `report_roster_change`). `ReportFactHandlers` hands each event to `JdbcKeptFacts`; `KeptFactsSource` reads the week back.
  - It inserts or updates each row by the id it came with.
  - It ignores an update that is older than the row, by the event's ordering field.
  - An update that arrives before the event that creates the record is kept; what only the creating event carries (a visit's service and end, an incident's category, description and report time) is filled in when that event arrives. An incident is in no week until `IncidentRaised` has said when it was reported.
- **Filed reports never change.** Facts that arrive later reach readers through amendments, as today.
- **First deployment.** Fill the tables once from the current data. While the schema is still shared, this is a one-off copy from the tables report read until then (platform migration V10). Each copied row takes the ordering field an event is compared with, so an event still on its way that is older than the copy changes nothing. Demonstration data, loaded straight into the databases, sends no events: load core's `db/demo/report-facts.sql` after it, which makes the same copy from visit's and core's databases.
- **When to generate.** Generate after the week has ended, leaving room for late check-outs and late events: Monday 02:00 Singapore time.

**Which week a fact belongs to.** A week runs from Monday 00:00 to the next Monday 00:00, Singapore time. The start
is included and the end is not.

| Fact | Elder | Week by |
|---|---|---|
| Visit | `elderId` | `scheduledStart` |
| Task, readings, confirmation, evidence | `elderId` | `visitScheduledStart` |
| Incident and its timeline | `elderId` | the incident's `reportedAt` |
| Spot check | `elderId` | `proposedTime` |
| Roster change | `elderId` | `visitStart` |

## 6. Gaps in today's data

These gaps already show in reports today. The events do not cause them. The owners of the code fix them, and the
catalogue already reserves the events they will need.

- **Nothing sets a visit to AUTO_CLOSED.** A visit the elder confirms becomes VERIFIED (`VisitClosed`), but one the elder never answers stays COMPLETED, so its week is marked incomplete (`data_complete`).
- **Readings are stored with `out_of_range = false`.** The caregiver's own judgement is the health record's flag. `VitalsRecorded` carries both, so report can follow whichever rule the owners decide.
- **Private evidence uploads publish count snapshots.** `VisitEvidenceUpdated` updates report's separate V5 projection without changing visit execution state. Replays do not publish twice; rollback does not publish. Uploads remain UNVERIFIED; this does not add an evidence-verification workflow. Older copied counters remain the fallback when no snapshot exists.
- **The value-added availability check was 8 hours off.** It bound `LocalDateTime` straight into SQL, while visit times are written through JPA, so the overlap check was 8 hours off in a JVM running on UTC. It is the calls in section 4 now, and visit binds the times the way it writes them.
