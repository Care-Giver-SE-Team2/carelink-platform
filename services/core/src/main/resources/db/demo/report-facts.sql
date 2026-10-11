-- =====================================================================
-- Demonstration data: report's record of it
-- =====================================================================
--
-- NOT A FLYWAY MIGRATION (no V prefix), for the reasons given at the top of
-- demo-seed.sql. LOAD IT LAST, after every other file here.
--
-- report keeps the facts its reports are made from in its own tables, filled
-- from the events visit and core publish (docs/platform/event-catalogue.md,
-- section 5). Demonstration data goes straight into the databases and sends no
-- events, so this copies the facts in instead, the way the first deployment
-- did (platform migration V10): visit's from visit's database, carelink_visit,
-- and core's from core's. Load it with an account that can read both.
--
-- SAFE TO RUN AGAIN. Every statement refreshes the rows it copies and adds none
-- twice; each copied row takes the ordering field an event is compared with, so
-- an older event still on its way changes nothing.
-- =====================================================================

INSERT INTO report_visit (visit_id, elder_id, scheduled_start, scheduled_end, service_type, care_plan_id,
                          care_plan_node_id, status, caregiver_id, checked_in_at, checked_out_at, version,
                          evidence_count, verified_evidence_count)
SELECT * FROM (
    SELECT v.id AS visit_id, v.elder_id, v.scheduled_start, v.scheduled_end, v.service_type, v.care_plan_id,
           v.care_plan_node_id, v.status, v.caregiver_id, v.checked_in_at, v.checked_out_at, v.version,
           (SELECT COUNT(*) FROM carelink_visit.visit_evidence e WHERE e.visit_id = v.id) AS evidence_count,
           (SELECT COUNT(*) FROM carelink_visit.visit_evidence e WHERE e.visit_id = v.id AND e.verification_status = 'VERIFIED')
               AS verified_evidence_count
    FROM carelink_visit.visit v) AS copied
ON DUPLICATE KEY UPDATE
    elder_id = copied.elder_id, scheduled_start = copied.scheduled_start, scheduled_end = copied.scheduled_end,
    service_type = copied.service_type, care_plan_id = copied.care_plan_id,
    care_plan_node_id = copied.care_plan_node_id, status = copied.status, caregiver_id = copied.caregiver_id,
    checked_in_at = copied.checked_in_at, checked_out_at = copied.checked_out_at, version = copied.version,
    evidence_count = copied.evidence_count, verified_evidence_count = copied.verified_evidence_count;

INSERT INTO report_visit_task (task_id, visit_id, elder_id, visit_scheduled_start, name, status, caregiver_note,
                               recorded_at, sequence)
SELECT * FROM (
    SELECT t.id AS task_id, t.visit_id, v.elder_id, v.scheduled_start AS visit_scheduled_start, t.name, t.status,
           t.caregiver_note, t.completed_at AS recorded_at,
           (SELECT COALESCE(MAX(o.id), 0) FROM carelink_visit.outbox_event o) AS sequence
    FROM carelink_visit.visit_task t
    JOIN carelink_visit.visit v ON v.id = t.visit_id) AS copied
ON DUPLICATE KEY UPDATE
    visit_id = copied.visit_id, elder_id = copied.elder_id, visit_scheduled_start = copied.visit_scheduled_start,
    name = copied.name, status = copied.status, caregiver_note = copied.caregiver_note,
    recorded_at = copied.recorded_at, sequence = GREATEST(report_visit_task.sequence, copied.sequence);

-- A reading recorded by an event is matched by its health record and metric, and takes its source id
INSERT INTO report_vital_sign (health_record_id, source_id, visit_id, elder_id, visit_scheduled_start, metric, value,
                               unit, out_of_range, recorded_at)
SELECT * FROM (
    SELECT s.health_record_id, s.id AS source_id, s.visit_id, v.elder_id, v.scheduled_start AS visit_scheduled_start,
           s.metric, s.value, s.unit, s.out_of_range, s.recorded_at
    FROM carelink_visit.vital_sign s
    JOIN carelink_visit.visit v ON v.id = s.visit_id) AS copied
ORDER BY copied.source_id
ON DUPLICATE KEY UPDATE source_id = copied.source_id, out_of_range = copied.out_of_range;

INSERT INTO report_elder_confirmation (visit_id, elder_id, visit_scheduled_start, confirmation_status, rating, comment,
                                       confirmed_at)
SELECT * FROM (
    SELECT c.visit_id, v.elder_id, v.scheduled_start AS visit_scheduled_start, c.confirmation_status, c.rating,
           c.comment, c.confirmed_at
    FROM carelink_visit.elder_confirmation c
    JOIN carelink_visit.visit v ON v.id = c.visit_id) AS copied
ON DUPLICATE KEY UPDATE
    elder_id = copied.elder_id, visit_scheduled_start = copied.visit_scheduled_start,
    confirmation_status = copied.confirmation_status, rating = copied.rating, comment = copied.comment,
    confirmed_at = copied.confirmed_at;

INSERT INTO report_incident (incident_id, elder_id, visit_id, category, description, reported_at, severity, status,
                             resolved_at, last_log_id)
SELECT * FROM (
    SELECT i.id AS incident_id, i.elder_id, i.visit_id, i.category, i.description, i.reported_at, i.severity,
           i.status, i.resolved_at, (SELECT MAX(l.id) FROM incident_log l WHERE l.incident_id = i.id) AS last_log_id
    FROM incident i) AS copied
ON DUPLICATE KEY UPDATE
    elder_id = copied.elder_id, visit_id = copied.visit_id, category = copied.category,
    description = copied.description, reported_at = copied.reported_at, severity = copied.severity,
    status = copied.status, resolved_at = copied.resolved_at, last_log_id = copied.last_log_id;

INSERT INTO report_incident_log (log_id, incident_id, actor, action, detail, occurred_at)
SELECT * FROM (
    SELECT l.id AS log_id, l.incident_id, l.actor, l.action, l.detail, l.occurred_at
    FROM incident_log l) AS copied
ON DUPLICATE KEY UPDATE
    incident_id = copied.incident_id, actor = copied.actor, action = copied.action, detail = copied.detail,
    occurred_at = copied.occurred_at;

INSERT INTO report_spot_check (spot_check_id, elder_id, visit_id, caregiver_id, proposed_time, approval_status, result,
                               outcome, finding, caregiver_response, checked_at, sequence)
SELECT * FROM (
    SELECT s.id AS spot_check_id, s.elder_id, s.visit_id, s.caregiver_id, s.proposed_time, s.approval_status,
           s.result, s.outcome, s.finding, s.caregiver_response, s.checked_at,
           (SELECT COALESCE(MAX(o.id), 0) FROM outbox_event o) AS sequence
    FROM spot_check s) AS copied
ON DUPLICATE KEY UPDATE
    elder_id = copied.elder_id, visit_id = copied.visit_id, caregiver_id = copied.caregiver_id,
    proposed_time = copied.proposed_time, approval_status = copied.approval_status, result = copied.result,
    outcome = copied.outcome, finding = copied.finding, caregiver_response = copied.caregiver_response,
    checked_at = copied.checked_at, sequence = GREATEST(report_spot_check.sequence, copied.sequence);

INSERT INTO report_roster_change (roster_change_id, visit_id, elder_id, visit_start, original_caregiver_id, status,
                                  outcome, decided_by, assigned_caregiver_id, sequence)
SELECT * FROM (
    SELECT rc.id AS roster_change_id, rc.visit_id, rc.elder_id, rc.visit_start, rc.original_caregiver_id, rc.status,
           rc.outcome, rc.decided_by, rc.assigned_caregiver_id,
           (SELECT COALESCE(MAX(o.id), 0) FROM outbox_event o) AS sequence
    FROM roster_change rc) AS copied
ON DUPLICATE KEY UPDATE
    visit_id = copied.visit_id, elder_id = copied.elder_id, visit_start = copied.visit_start,
    original_caregiver_id = copied.original_caregiver_id, status = copied.status, outcome = copied.outcome,
    decided_by = copied.decided_by, assigned_caregiver_id = copied.assigned_caregiver_id,
    sequence = GREATEST(report_roster_change.sequence, copied.sequence);
