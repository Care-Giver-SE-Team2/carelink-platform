-- CG03: latest visit observation plus immutable measurement batches.
-- NULL means not recorded, not "healthy". Existing readings remain unbatched.
ALTER TABLE visit
    ADD COLUMN health_flag VARCHAR(32) NULL,
    ADD COLUMN health_note VARCHAR(1000) NULL,
    ADD CONSTRAINT chk_visit_health_flag CHECK
        (health_flag IS NULL OR health_flag IN ('NO_CONCERN','ATTENTION','MEDICAL_REVIEW'));

CREATE TABLE visit_health_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    visit_id BIGINT NOT NULL,
    recorded_by_user_id BIGINT NOT NULL,
    health_flag VARCHAR(32) NOT NULL,
    health_note VARCHAR(1000) NULL,
    recorded_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_health_record_visit (id, visit_id),
    KEY idx_health_record_history (visit_id, recorded_at, id),
    CONSTRAINT fk_health_record_visit FOREIGN KEY (visit_id) REFERENCES visit(id),
    CONSTRAINT chk_health_record_flag CHECK
        (health_flag IN ('NO_CONCERN','ATTENTION','MEDICAL_REVIEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE vital_sign
    MODIFY COLUMN recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN health_record_id BIGINT NULL,
    ADD CONSTRAINT fk_vital_health_record FOREIGN KEY (health_record_id, visit_id)
        REFERENCES visit_health_record(id, visit_id),
    ADD UNIQUE KEY uq_health_record_metric (health_record_id, metric);
