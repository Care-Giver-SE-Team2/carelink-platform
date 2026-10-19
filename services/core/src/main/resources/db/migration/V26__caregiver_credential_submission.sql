-- Caregiver-owned submissions; existing MG06 review and historical credential rows stay intact.
CREATE TABLE caregiver_credential_submission_guard (
    caregiver_id BIGINT NOT NULL PRIMARY KEY,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_ccsg_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE credential_document (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    credential_id BIGINT NOT NULL,
    storage_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL COMMENT 'soft FK to app_user.id',
    uploaded_at DATETIME NOT NULL,
    display_order SMALLINT NOT NULL,
    UNIQUE KEY uk_credential_document_storage(storage_key),
    UNIQUE KEY uk_credential_document_order(credential_id, display_order),
    CONSTRAINT fk_credential_document_credential FOREIGN KEY (credential_id) REFERENCES credential(id),
    CONSTRAINT ck_credential_document_size CHECK (byte_size > 0),
    CONSTRAINT ck_credential_document_order CHECK (display_order BETWEEN 0 AND 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE credential_submission (
    credential_id BIGINT NOT NULL PRIMARY KEY,
    submitted_by_user_id BIGINT NOT NULL COMMENT 'soft FK to app_user.id',
    client_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action ENUM('NEW','RENEWAL','RESUBMIT') NOT NULL,
    resubmits_credential_id BIGINT NULL,
    payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    submitted_at DATETIME NOT NULL,
    result_snapshot JSON NOT NULL,
    UNIQUE KEY uk_credential_submission_request(submitted_by_user_id, client_request_id),
    UNIQUE KEY uk_credential_submission_resubmit(resubmits_credential_id),
    CONSTRAINT fk_credential_submission_credential FOREIGN KEY (credential_id) REFERENCES credential(id),
    CONSTRAINT fk_credential_submission_resubmit FOREIGN KEY (resubmits_credential_id) REFERENCES credential(id),
    CONSTRAINT ck_credential_submission_action CHECK (
        (action = 'RESUBMIT' AND resubmits_credential_id IS NOT NULL)
        OR (action <> 'RESUBMIT' AND resubmits_credential_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
