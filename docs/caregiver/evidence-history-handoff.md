# Visit evidence, own execution history and SYS03 handoff

Local implementation for plan 016 E/F, based on main dbfcce1. Not a claim of deployment, manager UI acceptance or remote CI success.

## Ownership boundaries

- Manager UI, approval controllers, roster algorithms, report projections and the family Observer are unchanged.
- SYS03 continues to call the existing incident route once. Only its caregiver notification audience changes to the assigned caregiver at trigger time; manager recipients remain as before. No new family notification publisher.
- New notifications use resource type `CAREGIVER_SYSTEM_ALERT`, opening `/caregiver/system-alerts/{incidentId}`. The safe summary is scoped to the stored trigger caregiver, not every caregiver. Reassignment preserves only that original minimal summary, never elder identity or the new work pack. Old already-stored INCIDENT notifications are not rewritten; use System alerts to read their attributable trigger summaries.
- CG06 is a read-only first slice. No bulk export, offboard access or manager assessment is included. It does not represent full CG06 long-term retention policy.

## Database and files

These tables are visit's (`services/visit`, its own database). Visit V3 indexes SYS03 trigger ownership. V4 adds `visit_evidence_file`, keyed to `visit_evidence.id`, with uploader identity, private storage key, MIME, byte length, sanitized filename, original/stored hashes, optional task and server submission time. V5 indexes caregiver command history by actor/time/visit. Published V1–V2 are unchanged.

Use `carelink.caregiver.evidence-storage` (environment `CARELINK_EVIDENCE_STORAGE`) for a private persistent directory. It must not be web-served or inside static resources. The local default is `target/private/evidence` relative to the working directory; the image sets `/tmp/carelink/private/evidence`, which is pod-local (an `emptyDir`) until a persistent volume is mounted there. The caregiver's own spot checks in the history come from core (`GET /internal/v1/caregivers/by-username/{username}/spot-checks`), and who is alerted for a missed check-in is core's (`NotificationRequestedIncidentAlert`).

Only PHOTO/SIGNATURE and JPEG/PNG are supported. Up to 5 MiB input and re-encoded output, 20 megapixels, 20 files/Visit by default; configuration may lower, not raise, these limits. Metadata is removed by decoding/re-encoding. This is not antivirus scanning, authenticity verification, elder confirmation or a clinical interpretation.

The command locks the Visit, rechecks assignment/state/version, validates task ownership and file count, and persists metadata, version, audit and receipt atomically. Writes require IN_PROGRESS and check-in. A file is written before the DB reference; confirmed rollback removes that generated file. Uncertain commit outcome retains it for reconciliation. Check-out remains independent of tasks and evidence; new evidence remains UNVERIFIED and never finishes a task.

## Two different read rights

1. `/api/visits/{visitId}/evidence` and its content endpoint use current assignment, an enabled AVAILABLE/BUSY account, and reject cancelled Visits. Legacy arbitrary reference strings are never interpreted as file paths. Neither private-file route introduces offboard access.
2. `/api/caregivers/me/history/{visitId}/evidence/{evidenceId}/content` uses immutable uploader identity and an enabled AVAILABLE/BUSY account. It allows that uploader to review only their own image after reassignment/cancellation. It grants no access to somebody else's upload or current work pack. INACTIVE accounts cannot use this historical route.

Downloads verify size/hash and return attachment, `no-store` and `nosniff`. Missing/corrupt storage is reported as unavailable; no invented download success. Authorized reads are audited.

## CG06 history provenance

Month selection uses command receipt `occurred_at` in Asia/Singapore, not planned Visit date. A Visit appears only if this user has at least one actual command in the month. The summary aggregates all this user's commands for that Visit, with own check-in/out timestamps where recorded; status is current Visit status, not a performance rating.

Details are paginated (1–50) and project only attributed command sources: task result, immutable health record/readings, own upload metadata, own reported incident and concluded own spot checks. No elder profile or manager-private timeline. Existing task/result status and incident status are current projections of the attributable source, not frozen reports. Old Visits without submitting-identity receipts are not retroactively assigned to a caregiver.

## Recovery and storage operations

- Back up the database and evidence/credential directories together while writes are stopped. A DB-only backup does not contain images. Restore only to a separate test volume/directory first and verify hashes and authenticated downloads.
- Before deleting any possible orphan, stop writes and export a fresh JSON array of `storageKey`, `byteSize`, `sha256` from `visit_evidence_file`. Run `python -B scripts/audit-visit-evidence.py --root <private-dir> --manifest <protected-json>`.
- The script is read-only and never deletes. Review missing, corrupt, unsafe or orphan-candidate entries; an orphan may be an in-flight/unknown-outcome transaction or belong to another DB snapshot. Do not run age-only/glob cleanup. Retain uncertain files until the DB and backup are reconciled and deletion is explicitly approved.
- Rollback of the feature means disabling entry points while retaining facts/files; do not edit released migrations or erase execution facts.

## Acceptance boundaries

Tests cover database rollback (metadata/receipt), command replay/conflict, upload/check-out competition, count/version competition, current versus historical ownership, legacy paths, image validation and normal work with incomplete tasks. Browser, restart and full CI-equivalent results are recorded in the local plan, not inferred from these capabilities. Remote CI requires a future explicitly authorized push.

Local acceptance on 2026-10-12/13: full backend verification passed 1,627 unit/architecture checks and 840 real-MySQL integration checks, with domain coverage gates met. The final security/API-fragment additions separately passed 41 unit/architecture and 22 real-MySQL checks (overlapping tests, not additive totals). A paired database/private-file backup was restored to an independent 8087 instance and authenticated reads succeeded. The user also confirmed the real Visit 2 browser upload with `Evidence saved — Unverified`; subsequent authenticated readback confirmed the saved file. A fresh paired backup includes all three fictional images with no reconciliation issues. See external plan 016 for the final frontend run and demo build; no remote CI is implied.

Remaining team consumers: manager admission HTTP/UI and work-settings Reader consumption; agreed service catalog/UNAVAILABLE policy; manager evidence verification/read contract; report health-text projection and cross-role demonstration. This change does not implement those owners' rules.
