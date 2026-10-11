-- visit has a database of its own (docs/platform/service-boundaries.md, section 9), built by its own
-- migrations, so its tables leave core's. Nothing in core reads or writes them: core asks visit through
-- VisitApi, and report keeps what it needs from visit's events (V9). V10 has already copied them for
-- report; core's tables lost their foreign keys to them in V3.
--
-- The schema split starts from empty service databases; rows kept here before are not carried over.
-- The tables that point at a visit go first.
DROP TABLE vital_sign;
DROP TABLE visit_health_record;
DROP TABLE visit_task;
DROP TABLE visit_evidence;
DROP TABLE visit_state_transition;
DROP TABLE visit_assignment;
DROP TABLE elder_confirmation;
DROP TABLE caregiver_command_receipt;
DROP TABLE visit_check_in_record;
DROP TABLE visit_missed_check_in_trigger;
DROP TABLE visit;
