-- report has a database of its own (docs/platform/service-boundaries.md, section 9), built by its own
-- migrations, so its tables leave core's: its reports and value-added services, and the facts it keeps
-- from visit's and core's events (V9). Nothing in core reads or writes them; core's tables lost their
-- foreign keys to them in V3.
--
-- The schema split starts from empty service databases; rows kept here before are not carried over.
-- The tables that point at another go first.
DROP TABLE report_amendment;
DROP TABLE report;
DROP TABLE report_basis;
DROP TABLE value_added_service_request;
DROP TABLE value_added_service;
DROP TABLE caregiver_review;
DROP TABLE report_visit;
DROP TABLE report_visit_task;
DROP TABLE report_vital_sign;
DROP TABLE report_elder_confirmation;
DROP TABLE report_incident;
DROP TABLE report_incident_log;
DROP TABLE report_spot_check;
DROP TABLE report_roster_change;
