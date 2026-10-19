# Caregiver admission and work settings — integration contract

Local implementation on `codex/caregiver-onboarding-work-settings`, based on main `dbfcce1`.
Not pushed, merged, or deployed. Manager HTTP/UI consumers and roster consumption are **not implemented by this batch**.

## Ownership and independent decisions

Admission approves a person to work. Credential review approves an individual certificate. Neither decision performs the other. An application with zero certificates can be approved. Service-specific credential requirements remain unchanged.

Registration creates an enabled `CAREGIVER` login and an `ONBOARDING` profile in the same transaction as a pending application. Enabled means the account can authenticate, not that it can work. Pending/rejected applicants may maintain their application, optional certificates and work settings; work-pack, schedule, check-in/out, task/health writes, leave and spot-check work are restricted. Existing AVAILABLE/BUSY profiles without application records remain valid legacy accounts.

Approval changes PENDING to APPROVED and ONBOARDING to AVAILABLE. It does not publish certificates, create visits, approve leave, change the manager-assigned sector or reactivate an INACTIVE/disabled account. Rejection requires a reason and leaves the profile ONBOARDING; editing and deliberate resubmission preserve immutable review snapshots.

## Database migrations

- V27: `caregiver_application`, `caregiver_application_history`, `caregiver_admission_state`, `caregiver_admission_receipt`.
- V28: `caregiver_work_settings_state`, `caregiver_work_settings_receipt`.
- Existing `caregiver_availability` and `caregiver_preference` hold settings. Existing V1–V26 migrations are unchanged. Admission and settings have separate caregiver-level versions and separate command receipts; no fictitious Visit ID is used.

Use Flyway on a fresh/compatible database. Do not overwrite an old demo volume or edit migration checksums. Migrations are local/unreleased and must be rechecked against main before publication.

## Caregiver HTTP endpoints

All paths below start with `/api`. Authentication is the existing session/CSRF mechanism. The public registration POST still requires CSRF. Unknown top-level identity/status fields are rejected.

| Method and path | Input / result |
| --- | --- |
| POST `/caregiver-registrations` | username, password, fullName, phone, optional declaredDialects/requestedSector; 201 admission view, no automatic login |
| GET `/caregivers/me/application` | admission view, application snapshot/history, version, compatibility and permitted actions |
| GET `/caregivers/me/profile` | current profile; existing GET `/caregivers/me` remains available |
| PUT `/caregivers/me/application` | editable details + expectedVersion + canonical UUID clientRequestId |
| POST `/caregivers/me/application/resubmissions` | expectedVersion + clientRequestId; rejected applications only |
| PUT `/caregivers/me/contact` | phone + expectedVersion + clientRequestId; active profiles only |
| GET `/caregivers/me/work-settings` | settings, directories, dates, edit permission, assigned bookings and leave for today + next 30 days |
| GET `/caregivers/me/availability?dateFrom=…&dateTo=…` | same projection for an inclusive range of at most 31 days |
| PUT `/caregivers/me/availability/{date}` | windows + expectedSettingsVersion + clientRequestId; replaces one complete date |
| PUT `/caregivers/me/work-preferences` | preferences object + expectedSettingsVersion + clientRequestId; replaces soft preferences |

Example day replacement:

```json
{
  "windows": [{"start":"09:00","end":"12:00","status":"UNAVAILABLE"}],
  "expectedSettingsVersion": 0,
  "clientRequestId": "11111111-1111-4111-8111-111111111111"
}
```

Example preferences (empty service selection is valid while the directory is unavailable):

```json
{
  "preferences": {
    "serviceCodes": [], "sectors": [],
    "weeklyWindows": [{"weekday":"MONDAY","start":"09:00","end":"12:00"}],
    "maxVisitsPerDay": 4, "maxHoursPerDay": 4.5
  },
  "expectedSettingsVersion": 1,
  "clientRequestId": "22222222-2222-4222-8222-222222222222"
}
```

Registration uses lower-case ASCII usernames (3–64), the existing password encoder, and a 72 UTF-8 byte password limit (minimum 8 characters). No password fingerprint/receipt is stored. On an unknown registration result, try normal login before resubmitting. Contact numbers normalize to Singapore mobile format; requestedSector is an intention and never overwrites authoritative caregiver.sector.

## Manager application ports to connect

`profile.application.CaregiverApplicationReader`:

- `pendingApplications(managerUsername, page, size)` returns real application records and total (size 1–100).
- `applicationForManager(managerUsername, applicationId)` returns the current application/history/version.

`profile.application.CaregiverAdmissionCommands`:

- `decide(managerUsername, applicationId, approve, reason, expectedVersion, clientRequestId)`.
- The application boundary checks an enabled MANAGER account, locks the profile, checks current status/version and records the real reviewer. Never take manager identity or approval status from a caregiver request.
- Same actor/key/payload returns the original result. Changed payload under a key or stale versions return a conflict. Mutation, activation, history, audit and receipt are one transaction.
- This port is not a public manager controller. The manager owner should replace application mock data/IDs and wire their own authenticated controller/UI to it. No real user approval is simulated through SQL for acceptance.

`rostering.application.CaregiverWorkSettingsReader.read(caregiverIds, from, to)`:

- Trusted server-side batch port, maximum 500 IDs / 31 inclusive days; no public cross-caregiver HTTP endpoint.
- Three batch persistence reads, not one query per caregiver; output has structured preferences/windows, version, updatedAt and dataIssues, never JPA entities/CSV.
- Missing caregiver IDs are omitted. Existing caregivers with no data return empty declarations, version 0, and `UNSPECIFIED` uncovered time.
- `consumptionStatus` is `NOT_APPLIED_TO_ROSTERING`. Do not infer that saving changes candidate search, recurring generation or current visits.

## Time, directory and leave semantics

- Asia/Singapore, same-day half-open [start,end); no overnight/24:00 or seconds. Adjacent windows are allowed, overlapping windows rejected regardless of status.
- Dates can be edited from today through day +90 inclusive, maximum 8 windows/day. Weekly preferred windows: maximum 14 total / 2 per weekday.
- Empty dates/uncovered time are UNSPECIFIED, not all-day unavailability. Empty preference arrays mean no preference, not rejection of every shift.
- Weekly times, service/area selections and personal workload limits are soft preferences. They never relax institution constraints. Defaults cap the input at 8 visits/8 hours and respect lower enabled institution limits; hours use half-hour steps.
- Approved whole-day leave takes precedence; pending leave is not effective approval. Settings do not cancel or reassign visits and do not request leave. The page shows existing bookings and leave separately.
- Sector choices currently come from existing profile sector identifiers, preserving leading zeros. This is not a new geographic catalog.
- No authoritative ordinary-service code directory exists in the current source. Default service choices are empty and the UI says so. After owners agree stable codes/mappings, set `carelink.caregiver.work-settings.service-codes` (comma-separated uppercase codes). Do not use arbitrary Visit task labels or report-only value-added IDs as equivalent codes.
- Legacy unknown codes are retained in responses and flagged; malformed legacy windows are flagged. Nothing is silently written back or erased on a read. The editor requires review confirmation before replacement; raw rows remain intact until explicit save.

## Error/retry contract

401 unauthenticated, 403 unauthorized/restricted, 400 malformed input, 409 business/version/request-key conflict. Important codes: `USERNAME_TAKEN`, `APPLICATION_VERSION_CONFLICT`, `APPLICATION_REQUEST_CONFLICT`, `WORK_SETTINGS_VERSION_CONFLICT`, `WORK_SETTINGS_REQUEST_CONFLICT`, `WORK_SETTINGS_INVALID`.

Keep a command's version and request key together. After a timeout/5xx, retry only the unchanged command; successful replay returns its original snapshot, even after later edits. After a stale-version conflict, refresh and review before a new command. Dates/catalog are validated on new writes, not used to reject replay of a previously successful command.

## Acceptance status and manager handoff checklist

Local targeted admission/settings acceptance: 20 real MySQL tests (12 admission, including concurrent same-name registration, + 8 work settings), 14 domain tests plus 4 architecture rules. The 8086 browser demo registered a zero-certificate applicant, authenticated with restricted navigation and persisted a date window. Broader batch regression is recorded in external plan 016; this is not a remote CI or manager-UI acceptance claim. Evidence/history/SYS03 contracts are in `evidence-history-handoff.md` and the OpenAPI local fragment `docs/api/caregiver-self-service.yaml`.

- [ ] Manager owner connects real application HTTP/UI and removes mock approval rows.
- [ ] Agree service-code directory and authoritative sector choices.
- [ ] Confirm how explicit UNAVAILABLE constrains assignment, and how AVAILABLE uncovered time is treated.
- [ ] Manager owner connects Reader in the selected MG03/MG04 paths and tests both recurring generation and reassignment.
- [ ] Agree application notification producer/target routing before sending application notifications; this batch does not emit links to mock manager IDs.
- [ ] Jointly demonstrate zero-certificate admission and real manager assignment through those consumers.

No manager page, ranking/assignment algorithm, credential review decision, report calculation or family notification observer was changed by A–D.
