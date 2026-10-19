CREATE INDEX idx_missed_check_in_caregiver_time
    ON visit_missed_check_in_trigger(triggered_caregiver_id, triggered_at, visit_id);
