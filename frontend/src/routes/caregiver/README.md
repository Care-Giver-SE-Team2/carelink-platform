# Caregiver client (platform partial port, 2026-10-19)

The selected caregiver feature scope now includes registration/admission, independent
private certificates, work settings, the existing schedule/execution flow, own alerts,
private evidence and receipt-based own history. See
[scope, ownership, persistence and verification](../../../../../docs/platform/caregiver-feature-migration.md).

Admission needs no certificate. Certificate approval does not admit a caregiver.
Availability/preferences (including UNAVAILABLE) remain advisory and are not consumed
by automatic rostering. A published visit needs no new accept/reject action. Existing
leave approval and explicit reassignment remain separate.

History covers the actor's recorded commands and authorized own spot-check facts.
FullCG06 export/offboarding/retention, a new evidence verification workflow, manager
preference editing and unrelated elder confirmation changes were not included.
Private uploads stay UNVERIFIED. Existing6b identity-switch and cross-tab failures
remain open; a new route is not evidence that these defects have been fixed.

Route ownership matters: admission/credentials/work settings go to core;
history/system alerts/evidence go to visit. Reuse platform shared API authentication
and CSRF. For FormData, allow the browser to set the multipart boundary.

Local frontend commands remain `npm ci`, `npm run dev`, `npm test` and
`npm run build` in frontend. These require independently configured local backend,
Redis and service databases; older single-database demo SQL is not a platform fixture.
The migration was exercised only with synthetic local data. No cloud deployment or
live data migration is implied.
