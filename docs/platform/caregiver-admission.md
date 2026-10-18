# Caregiver admission: partial upstream port

An anonymous applicant may register without a certificate. Registration creates an enabled
CAREGIVER account, an ONBOARDING profile and a PENDING application in one core transaction.
It does not sign the applicant in. A manager's approval changes the application to APPROVED and
the profile to AVAILABLE. Rejection retains ONBOARDING and records a reason; the applicant may
edit and resubmit. Approval neither publishes certificates nor creates visits or assigns a sector.

## Ownership and endpoints

Core owns all four new admission tables (V26). Visit obtains caregiver status through the existing
CoreApi projection; it does not read core tables. Existing ingress/Vite routing sends these new
application paths to core. Work endpoints remain in visit, with core retaining leave and spot checks.

| Method and public path | Access / result |
| --- | --- |
| POST /api/caregiver-registrations | Anonymous + CSRF; 201 admission view; username, password, fullName, phone, optional declaredDialects/requestedSector |
| GET /api/caregivers/me/application | Own CAREGIVER admission view, version and history |
| GET /api/caregivers/me/profile | Own CAREGIVER profile, including ONBOARDING |
| PUT /api/caregivers/me/application | Own pending/rejected application details + expectedVersion/clientRequestId |
| POST /api/caregivers/me/application/resubmissions | Own rejected application + expectedVersion/clientRequestId |
| PUT /api/caregivers/me/contact | Active caregiver phone + expectedVersion/clientRequestId |
| GET /api/caregiver-applications?page=0&size=100 | MANAGER pending applications; size 1–100 |
| GET /api/caregiver-applications/{id} | MANAGER application detail |
| POST /api/caregiver-applications/{id}/approve | MANAGER; optional message + expectedVersion/clientRequestId |
| POST /api/caregiver-applications/{id}/reject | MANAGER; required reason + expectedVersion/clientRequestId |

All writes retain CSRF protection. Command request IDs are canonical UUIDs. A repeat of the same
command returns its recorded result; a reused key with changed content or an outdated version is
refused with 409. Registration has no command receipt: duplicate usernames return 409; after an
unknown registration outcome the UI asks the user to try signing in before submitting again.

## Access and compatibility

AVAILABLE/BUSY permits normal work. Pending/rejected applicants can sign in and maintain their
application, but schedule, work-pack, tasks, check-in/out, health writes, incident writes, leave
requests and spot-check responses enforce work admission on the server. Historical reads retain
the upstream INACTIVE policy where applicable; the simplified navigation offers only application
status to an inactive profile. Disabled accounts cannot be reactivated by an approval.

Existing AVAILABLE/BUSY caregivers without an application remain active (`LEGACY_ACTIVE`); no
approval history is fabricated. Existing ONBOARDING profiles without an application are instructed
to contact the manager. Automatically converting those profiles is outside this batch.

Certificate metadata comes from the existing certification register. File/scan fields are null:
private uploads, renewals and attachment retrieval are not part of this migration. The manager UI
currently loads the first 100 pending applications; the API supports pagination. Larger admission
queues need a separate UI pagination extension.

## Local verification and deployment boundary

Admission/manager integration tests use real MySQL and MockMvc. `CaregiverAdmissionAccessIT`
uses real visit HTTP/MySQL and CoreStandIn, explicitly not a real core process. The separate local
acceptance runner exercises real core, visit, Redis and the frontend, including upgrading V25 to V26
with a legacy caregiver and session retained. Results and scripts are recorded in files-2/plans.

Coordinate core and visit versions before a future rollout; mixed versions do not enforce the same
work-access contract. Confirm V26 remains available on the selected branch and back up the database.
No deployment, cloud configuration, HPA, file storage or report-worker change is included. This batch
does not fix the already recorded delayed logout Cookie or cross-tab stale-data problems, and is not
a new performance baseline. Re-run the selected 6b business and freshness checks on a fixed build
before comparing performance samples.


The remaining selected caregiver scope has since been ported locally. The earlier
attachment deferrals in this admission-only record describe that batch's boundary;
see [the complete selected feature port](caregiver-feature-migration.md) for current
private document, preference, evidence, alert and history contracts and validation.
