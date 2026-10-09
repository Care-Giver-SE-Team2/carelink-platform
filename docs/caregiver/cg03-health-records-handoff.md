# CG03 measured vital signs and health observations

Local implementation based on main `d4b0a56` (includes MG07 V19). Migration: V20.
Implementation and verification status is maintained in the external `files/PLAN/007-plan.md`.

Local acceptance completed: 1373 unit/architecture tests and 793 MySQL integration tests in the
full UTC build, followed by 13 health integration tests recompiled against final sources in the
Singapore JVM; 946 frontend tests plus 15 final focused tests passed. Coverage gates, type check
and production build passed. This is local verification, not a remote CI or deployment claim.
These counts describe the initial local acceptance before main synchronization and PR delivery;
the latest delivery/CI status is maintained in the external plan. The isolated fictional demo is
at `http://localhost:8083`.

## Contract for report owners

| Data | Stored fact | Meaning |
| --- | --- | --- |
| Blood pressure | `vital_sign.metric=systolic/diastolic`, unit `mmHg` | Two numeric readings, submitted together |
| Pulse | `metric=pulse`, unit `bpm` | Integer measurement |
| Temperature | `metric=temperature`, unit `°C` | Up to two fractional digits |
| Latest observation | `visit.health_flag` | NULL = not recorded; NO_CONCERN = no concern marked; ATTENTION = needs attention; MEDICAL_REVIEW = caregiver recommends medical review |
| Latest note | `visit.health_note` | Factual caregiver observation, maximum 1000 characters; not a diagnosis or manager conclusion |
| History | `visit_health_record` and `vital_sign.health_record_id` | Append-only measurement batches, including observation/note snapshots, recording account and server time |

All supplied readings share the batch's `recorded_at`. V20 extends the existing reading time to
DATETIME(6), retaining historical values. Timestamp binding/readback follows the existing Visit/JPA
convention, including Singapore business time and the production JDBC connection zone.
Legacy readings retain a NULL batch ID and old visits retain NULL health fields. No historical
reading is deleted, relabelled as a real caregiver submission, or backfilled as normal health.

The latest Visit flag/note is replaced only by a successful complete observation command. Earlier
batch observations remain readable to the currently assigned caregiver. Missing measurements produce
no `vital_sign` row, never a synthetic zero. An all-unmeasured batch requires a reason in the note.
Repeat measurement appends a new batch; editing/deleting/correcting earlier measurements is outside
this delivery. Duplicate retries do not append records.

There is no clinical threshold engine. New readings have `out_of_range=false`, meaning the metric
was not marked out of range; it is not evidence of clinical normality. Existing true values remain.
`health_flag` does not set every metric's out_of_range, open an incident or send a notification.
Use the existing CG04 report flow when an incident must be communicated to MG05/FM05.

## Existing report consumption and remaining downstream work

MG07 already reads `vital_sign` by the elder and Visit's scheduled report period. The real HTTP
acceptance test records readings through caregiver login/check-in/health POST, then generates
reports through the existing manager endpoint and reads the authorised family projection. Its
positive report fixture does not use SQL inserts for measurements.

Since V19 reports freeze a `report_basis`; a previously filed report for the same period is returned
idempotently. Record the facts before first generating that period. Recording later measurements
does not rewrite old reports. Corrections/follow-up use the report owner's existing workflow.

The report owner still needs to add any desired health flag/note consumption to their fact query,
basis snapshot and audience-specific assembler. These health fields are not copied to task notes
or automatically exposed to family APIs. Generic Visit JSON omits them; the authorised caregiver
work pack explicitly exposes a safe latest observation. Family/free-text health-note visibility
requires the report owner's explicit projection contract. No report layout, fulfilment calculation,
elder confirmation or manager workflow is changed by this feature.

## HTTP and behaviour

- POST `/api/visits/{visitId}/health-records`: expectedVersion, clientRequestId, nullable systolic,
  diastolic, pulse, temperature, mandatory healthFlag, optional/conditionally required healthNote.
  Current assigned CAREGIVER, CSRF and checked-in IN_PROGRESS required. 201 new / 200 same receipt.
  A valid old receipt may replay after an exception, without writing again; current assignment is
  still required. Same key with different content is 409.
- GET the same path with page/size: current assigned caregiver only; size 1–50, default 10;
  recorded_at/id descending. Only actual V20 batches, no private staff IDs or command metadata.
- GET work-pack: `healthObservation`, and `HEALTH_RECORD` in allowedActions when writing is legal.
- Saves lock the parent Visit and atomically write batch/readings/latest summary/version/receipt.
  Failure at any point rolls back all facts. Writes contend with task/incident commands on the same
  Visit lock/version. No artificial IN_PROGRESS self-transition is added.

The English work-pack form shares the execution write guard and immutable retry request. Unknown
results require reconciliation or retry of the same request; there are no automatic POST retries.
Refresh preserves drafts, loss of access clears protected data, and starting a new measurement
requires an explicit action with empty inputs and an unselected health flag.

## Manual confirmation

1. Open a currently assigned valid plan Visit and check in normally.
2. Fill the four measurement fields, choose an observation, and save. Refresh/relogin and check
   the displayed saved record, time and latest summary.
3. Confirm that an explicit repeat measurement preserves the previous record, while retrying an
   identical request creates no duplicate; paired blood pressure and concern-note rules apply.
4. Generate a previously unfiled report period containing this Visit, then inspect existing manager
   and authorised family vital sections. New health-note display remains the report owner's work.
5. Visit is still IN_PROGRESS with no check-out time. CG05 is the next blocking delivery for service
   completion, independently of recording vital signs.
