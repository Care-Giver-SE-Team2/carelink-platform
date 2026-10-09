-- FM01: service requests for existing, bound elders. The legacy intake approval must not consume these.
-- All submission details are immutable snapshots; created_at is supplied by the application in UTC.
CREATE TABLE care_service_application (
    id BIGINT NOT NULL AUTO_INCREMENT,
    applicant_family_member_id BIGINT NOT NULL,
    elder_id BIGINT NOT NULL,
    elder_snapshot JSON NOT NULL COMMENT 'Family basic details at submission, never the full elder entity',
    care_needs JSON NOT NULL,
    notes TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_service_application_family (applicant_family_member_id, created_at, id),
    KEY idx_service_application_elder (elder_id),
    CONSTRAINT fk_service_application_family FOREIGN KEY (applicant_family_member_id) REFERENCES family_member (id),
    CONSTRAINT fk_service_application_elder FOREIGN KEY (elder_id) REFERENCES elder (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
