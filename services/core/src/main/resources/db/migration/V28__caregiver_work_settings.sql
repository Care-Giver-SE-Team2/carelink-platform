-- Self-service versions are independent of Visit commands and admission decisions.
CREATE TABLE caregiver_work_settings_state (
    caregiver_id BIGINT NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NULL,
    CONSTRAINT fk_cwss_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE caregiver_work_settings_receipt (
    actor_user_id BIGINT NOT NULL,
    client_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    caregiver_id BIGINT NOT NULL,
    payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_snapshot JSON NOT NULL,
    PRIMARY KEY (actor_user_id,client_request_id),
    CONSTRAINT fk_cwsr_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
