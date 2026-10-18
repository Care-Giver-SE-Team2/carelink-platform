-- Private file metadata, independent from credentials and evidence verification.
CREATE TABLE visit_evidence_file (
    evidence_id BIGINT NOT NULL PRIMARY KEY,
    uploader_user_id BIGINT NOT NULL,
    storage_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    content_type VARCHAR(30) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    original_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sanitized_original_name VARCHAR(255) NOT NULL,
    visit_task_id BIGINT NULL,
    submitted_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_vef_evidence FOREIGN KEY(evidence_id) REFERENCES visit_evidence(id),
    CONSTRAINT fk_vef_task FOREIGN KEY(visit_task_id) REFERENCES visit_task(id),
    CONSTRAINT ck_vef_size CHECK (byte_size > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_evidence_visit_id ON visit_evidence(visit_id,id);
CREATE INDEX idx_evidence_uploader ON visit_evidence_file(uploader_user_id,evidence_id);
