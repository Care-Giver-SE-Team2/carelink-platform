-- Admission is independent of certificate review. Existing accounts and credentials stay intact.
CREATE TABLE caregiver_admission_state (
    caregiver_id BIGINT NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cgas_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE caregiver_application (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    caregiver_id BIGINT NOT NULL,
    status ENUM('PENDING','APPROVED','REJECTED') NOT NULL,
    round_number INT NOT NULL DEFAULT 1,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    declared_dialects VARCHAR(100) NULL,
    requested_sector VARCHAR(50) NULL,
    submitted_at DATETIME NOT NULL,
    reviewed_by_user_id BIGINT NULL,
    reviewed_at DATETIME NULL,
    review_reason VARCHAR(500) NULL,
    UNIQUE KEY uk_cga_caregiver (caregiver_id),
    KEY idx_cga_status_date (status,submitted_at,id),
    CONSTRAINT fk_cga_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver(id),
    CONSTRAINT ck_cga_round CHECK (round_number > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE caregiver_application_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL,
    snapshot JSON NOT NULL,
    occurred_at DATETIME NOT NULL,
    KEY idx_cgah_application (application_id,id),
    CONSTRAINT fk_cgah_application FOREIGN KEY (application_id) REFERENCES caregiver_application(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE caregiver_admission_receipt (
    actor_user_id BIGINT NOT NULL,
    client_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    caregiver_id BIGINT NOT NULL,
    payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_snapshot JSON NOT NULL,
    PRIMARY KEY (actor_user_id,client_request_id),
    CONSTRAINT fk_cgar_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
