-- A committed SYS03 fact is once per Visit for its lifetime, not per assignment/version.
CREATE TABLE visit_missed_check_in_trigger (
    visit_id BIGINT NOT NULL PRIMARY KEY,
    incident_id BIGINT NOT NULL UNIQUE,
    triggered_caregiver_id BIGINT NOT NULL,
    scheduled_start DATETIME(6) NOT NULL,
    check_in_due_at DATETIME(6) NOT NULL,
    observed_visit_version INT NOT NULL,
    triggered_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_missed_check_in_visit FOREIGN KEY (visit_id) REFERENCES visit(id),
    CONSTRAINT fk_missed_check_in_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
    CONSTRAINT fk_missed_check_in_caregiver FOREIGN KEY (triggered_caregiver_id) REFERENCES caregiver(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_visit_missed_check_in_scan ON visit(status, scheduled_start, id);
