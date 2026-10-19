# CG01 qualification upload — integration contract

Local implementation on `codex/caregiver-credential-upload`, based on main `1983076`.
Migration: `V26__caregiver_credential_submission.sql` (recheck numbering before publishing).
The matching operations and DTOs are in `docs/api/openapi.yaml`.

## Ownership and what is not changed

The caregiver module owns the My credentials page, certificate submission and renewal commands,
explicit retries after rejection, immutable submission facts,
private file storage and authorized read endpoints. MG06 owns manager review UI, existing
Publish/Reject behaviour, review notifications, onboarding and rostering eligibility.
No manager screen or approval algorithm is changed by this feature. Uploading is not approval,
registration, account activation or automatic rostering. No third-party OCR/antivirus is used.

## Stable database contract

Existing `credential` / `credential_type` and status values are unchanged; new records are
`SUBMITTED`, with server SGT timestamps and no reviewer facts. Permanent is explicitly selected
and stored as `9999-12-31`; the caregiver read DTO represents permanent expiry as null.

| Table | Meaning and invariants |
| --- | --- |
| credential_document | 1–3 immutable document rows per submitted credential; generated private storage key, display filename, MIME, stored byte size/hash, uploader, SGT upload time and order. Unique key/order. No public URL/BLOB/cascade deletion. |
| credential_submission | One immutable submission receipt per new credential; actor/canonical request UUID unique, normalized request hash, action, rejected source if retry, first-result JSON/time. Rejected source can have only one explicit retry. |
| caregiver_credential_submission_guard | One caregiver-level transactional lock, lazily created, serializing caregiver submissions across types. Existing manager approvals do not share this lock. |

The uploader's app_user reference follows existing soft-FK conventions; caregiver/credential
relationships use real FKs. Historic credentials may have no new rows or attachments.
New and old reviewed materials are never overwritten by a caregiver. No automatic cleanup of
historical evidence or credentials is introduced. This does not solve existing concurrent manager
Publish/Reject races; that remains a separately owned integration concern.

## Endpoints (all relative to /api)

| Method/path | Authorization/use |
| --- | --- |
| GET /caregivers/me/credential-types | Enabled caregiver; institution's actual catalogue |
| GET /caregivers/me/credentials?page=0&size=20 | Own rows newest ID first, including history; size 1–50 |
| GET /caregivers/me/credentials/{id} | Own credential only; another caregiver gets 404 |
| POST /caregivers/me/credentials | NEW; type required |
| POST /caregivers/me/credentials/{id}/renewals | Own approved parent; type inferred and cannot change |
| POST /caregivers/me/credentials/{id}/resubmissions | Own REJECTED source; retain original rejection, files and approved renewal parent |
| GET /credentials/{id}/documents | Owner caregiver or enabled MANAGER; old credentials return [] |
| GET /credentials/{id}/documents/{documentId}/content | Same read scope; document must belong to credential; downloads only |

All writes retain existing session/CSRF (`X-XSRF-TOKEN`). ONBOARDING may supplement certificates;
INACTIVE may read own history but cannot submit. Disabled users cannot read/upload. FAMILY/ELDER
cannot read private certificates, certificate numbers or rejection reasons through these endpoints.
The browser-generated multipart boundary must not be replaced with application/json.

Submission parts: `credentialTypeId`, optional `certificateNo` (100), required `issuingBody` (150),
`validFrom` (YYYY-MM-DD), `expiryDate` OR `noExpiry=true`, `clientRequestId` (canonical lowercase UUID),
one to three repeated **files** parts (not `files[]`). Dates use SGT business days, year 1000–9999;
expiry must include today and not precede start, future start is allowed. Each file is <=5 MiB,
total <=15 MiB (multipart envelope capped at 16 MiB).

Example after obtaining session cookies and a CSRF token through existing auth:

```sh
curl -b cookies.txt -H "X-XSRF-TOKEN: $CSRF" \
  -F 'credentialTypeId=1' -F 'certificateNo=DEMO-001' \
  -F 'issuingBody=Demo Training Institute' -F 'validFrom=2026-10-11' \
  -F 'expiryDate=2027-10-11' \
  -F 'clientRequestId=1617c3ed-01ab-45d5-a282-b5d43a2a7473' \
  -F 'files=@demo-proof.png;type=image/png' \
  http://localhost:8085/api/caregivers/me/credentials
```

Replace catalogue ID/dates and create a fresh UUID for each distinct submission. Retain the same
UUID, fields, filenames and original bytes if response is lost. Same key/same payload yields HTTP
200 with the original receipt, not a second record, even after approval. Different payload gets
409 `REQUEST_KEY_REUSED`. Never turn a network/5xx ambiguity into a new submission key.

```json
{"credentialId":17,"status":"SUBMITTED","submittedAt":"2026-10-11T10:00:00","documentIds":[21]}
```

To show current review status, refresh own detail instead of interpreting the original receipt
as today's status. Read DTO includes `historical` (presentation hint, never authorization),
`renewsCredentialId`, `resubmitsCredentialId`, `reviewNote`, `reviewedAt` and `documents`.
Future-effective approved renewal does not hide the old current record or clear its CG01 reminder.
Historical material is collapsed in the list but expanded in its direct detail.

## Manager owner: minimal integration steps

1. Keep the existing register `GET /api/credentials` and existing Publish/Reject.
2. Use register row ID to fetch `/api/credentials/{id}/documents`. Do not pass caregiver identity.
3. Show honest missing/unavailable states; use each document ID in the authenticated content URL.
4. Treat PDF as download, not inline HTML/iframe. Browser session is sufficient for same-origin GET;
   downloads use attachment, no-store and nosniff. Do not cache or publish a file URL.
5. Existing rejection reason appears in the caregiver detail after refresh; explicit retry creates a
   new submitted row. Confirm directory/onboarding policy separately; this feature does not activate users.

CG01 alert graph removes only explicitly superseded, valid rejected retry attempts. Other ambiguous
forks, mismatched types/parents or cycles retain conservative manager-review behaviour.
SYS01 scheduling/notification routing is unchanged; upload does not send a duplicate expiry event.
Existing manager coverage policy is independently owned: confirm future-validFrom and repeated rejected
renewal graph handling with that owner rather than assuming caregiver reminders change their decisions.

## Storage, operations and deployment

Supported JPEG/PNG are bounded to 20 MP and re-encoded without source metadata. PDFs must be simple,
unencrypted, <=20 pages and contain no unsupported actions/attachments; parsing runs in a bounded
child JVM (96 MiB heap, 5 seconds, max 2 concurrent workers). This is not authenticity certification
or a claim of antivirus protection. Corrupt or missing persisted bytes fail download, not review state.

Credentials are core's. Local defaults are relative to the working directory and stay under the
git-ignored `target/`: `target/private/credentials` and `target/private/multipart`. Configure absolute
**private, writable, non-static** directories via `CARELINK_CREDENTIAL_STORAGE` and
`CARELINK_MULTIPART_TEMP`. The image (`build/Dockerfile`) sets them under `/tmp/carelink/private/`,
the only writable volume under Kubernetes' read-only root filesystem. That volume is an `emptyDir`,
so uploaded files are pod-local and lost when the pod is replaced, until a persistent volume (or
object storage) is mounted there; with more than one core replica a document is readable only
from the pod that stored it.

Back up the DB and private documents as a consistent pair, with submissions paused; preserve metadata
hash/size/storage keys and verify them on restore in an isolated environment before switching traffic.
Never use `down -v` or delete production volumes to upgrade. DB rollback cleans only this command's
new files; UNKNOWN commit outcome retains files for reconciliation. Crash/cleanup failures can leave
orphan files. There is intentionally no aggressive automatic orphan deletion: use a read-only inventory,
protection period and DB-reference check under a maintenance window before any separately authorized
cleanup. Database metadata alone is not a certificate backup. Monitor disk capacity and restrict OS access.

## Acceptance and outstanding collaboration

Local tests/demo results are recorded in external `files/PLAN/014-plan.md`; do not claim hosted CI
success for unpushed changes. Check actual upload, owner/manager download, another caregiver/family
denial, existing rejection/approval via APIs, renewal reminders, unknown-result retry, rollback,
restart/file persistence and unrelated schedule/execution regression.
Manager attachment UI, onboarding status, genuine institution catalogue and existing cross-policy
coverage discrepancies require owner confirmation; they are not implemented by this caregiver batch.
