-- CG04/CG03: no care narrative is duplicated in the command ledger.
CREATE TABLE caregiver_command_receipt (
    actor_user_id BIGINT NOT NULL,
    client_request_id CHAR(36) NOT NULL,
    action_code VARCHAR(30) NOT NULL,
    visit_id BIGINT NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    result_id BIGINT NOT NULL,
    visit_version INT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (actor_user_id, client_request_id),
    KEY idx_cg_command_result (actor_user_id, action_code, result_id),
    CONSTRAINT fk_cg_command_visit FOREIGN KEY (visit_id) REFERENCES visit(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_incident_caregiver_reports ON incident(reported_by_user_id, source, reported_at, id);
