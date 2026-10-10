# Event catalogue

This catalogue lists the events the services publish to each other: what each event carries and who handles it.

Version 1 was worked out from what report reads from other modules' tables today (`ReportFactsJdbcSource` and
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

**Visit's events.** visit publishes these. Until visit moves out of core, visit's code inside core publishes them.

| Event | Published by | When | Handled by | Status |
|---|---|---|---|---|
| `VisitScheduled` | `VisitSchedulingService.schedule`, `StandaloneVisitsService.schedule`, `VisitReassignmentService.moveTo` | A visit is created | report | with the move |
| `VisitCaregiverChanged` | `VisitSchedulingService.schedule` (when it covers an existing visit), `VisitReassignmentService.reassign`, `markUncovered` | The visit's caregiver changes | report | with the move |
| `VisitCancelled` | `VisitSchedulingService.cancelUntouchedFrom`, `VisitReassignmentService.callOff` | The visit becomes CANCELLED | report | with the move |
| `VisitCheckedIn` | `CaregiverVisitExecutionService.checkIn` | A caregiver checks in, including a check-in that resumes after a missed one | report | with the move |
| `VisitCompleted` | `CaregiverVisitExecutionService.checkOut` | The visit becomes COMPLETED | report: the weekly report and value-added settlement | with the move |
| `VisitExceptionRaised` | `CaregiverIncidentReportingService.report`, `VisitSchedulingService.markUncoveredAsException`, `VisitReassignmentService.markUncovered` | The visit becomes EXCEPTION | report | with the move |
| `VisitTaskRecorded` | `CaregiverVisitExecutionService.taskResult` | A task's result is recorded | report | with the move |
| `VitalsRecorded` | `CaregiverHealthService.record` | A set of readings is recorded | report | with the move |
| `ElderConfirmationSubmitted` | `VisitService.submitElderConfirmation` | The elder confirms or disputes a visit | report | with the move |
| `VisitClosed` | No code sets VERIFIED or AUTO_CLOSED yet | The visit becomes VERIFIED or AUTO_CLOSED | report | reserved |
| `VisitEvidenceUpdated` | No code writes visit evidence yet | Evidence is added or verified | report | reserved |

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
| `NotificationRequested` | core: the credential expiry alert (`NotificationRequestedCredentialAlert`) today. The six other core classes that insert into `notification` (incident 3, profile 1, rostering 2), and report's two, change to it | A message should reach someone | notification | published |
| `ReportRequested` | EventBridge Scheduler, straight into report's queue | Once a week | the report worker | with the report move |

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

`caregiverId` is null when nobody takes the visit over. `absenceId` is set when an absence caused the change.

**`VisitCancelled`**

```json
{ "visitId": 812, "elderId": 101, "scheduledStart": "2026-10-12T09:00:00+08:00", "reason": "ABSENCE",
  "absenceId": 33, "note": "Family skipped the visit", "version": 2, "cancelledAt": "2026-10-11T18:02:00+08:00" }
```

`reason` is one of:
- `PLAN_CHANGED`: `cancelUntouchedFrom`.
- `ABSENCE`: `callOff` with an absence.
- `CALLED_OFF`: `callOff` without an absence, for example when an extra service is withdrawn.

`note` is the reason text given to `callOff`.

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
{ "visitId": 812, "elderId": 101, "scheduledStart": "2026-10-12T09:00:00+08:00", "cause": "CAREGIVER_REPORT",
  "incidentId": 601, "checkedIn": true, "version": 5 }
```

- `cause` is `CAREGIVER_REPORT`, `UNCOVERED_AT_START` or `ABSENCE_UNCOVERED`.
- `incidentId` is null when the change raised no incident in the same transaction.

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

## 4. Asked when needed, not sent as events

- **Master data at generation time.**
  - Some facts rarely change and are read once per report: the elder's profile, the elder's current published care plan (version and weekly hours), the primary caregiver, and caregivers' names.
  - report asks core for them when it generates, and keeps them in the report's facts snapshot, as it does today.
  - One call answers all of it: `CoreApi.elderReportProfile`. A caregiver's name is `findCaregiverPublicProfile`.
- **"Is this caregiver free right now?"** Value-added dispatch needs the answer at that moment, so it asks rather than keeping a copy that could lag:
  - core: is the caregiver on approved leave that day? `CoreApi.onLeave`
  - visit: does the caregiver have an active visit that overlaps this time? `VisitApi.caregiverBusy`

## 5. What report keeps

- **Tables.** report keeps one table per kind of fact.
  - It inserts or updates each row by the id it came with.
  - It ignores an update that is older than the row, by the event's ordering field.
- **Filed reports never change.** Facts that arrive later reach readers through amendments, as today.
- **First deployment.** Fill the tables once from the current data. While the schema is still shared, this is a one-off copy from the tables report reads today.
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

- **Nothing sets a visit to VERIFIED or AUTO_CLOSED.** So every week with a completed visit is marked incomplete (`data_complete`). `VisitClosed` is reserved for this.
- **Readings are stored with `out_of_range = false`.** The caregiver's own judgement is the health record's flag. `VitalsRecorded` carries both, so report can follow whichever rule the owners decide.
- **Nothing writes visit evidence.** So evidence counts are 0. `VisitEvidenceUpdated` is reserved for this.
- **The value-added availability check is 8 hours off.** It binds `LocalDateTime` straight into SQL, while visit times are written through JPA, so the overlap check is 8 hours off in a JVM running on UTC. This goes away when the check becomes the calls in section 4.
