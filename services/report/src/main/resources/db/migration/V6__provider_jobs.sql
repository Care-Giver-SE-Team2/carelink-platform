-- Value-added requests that no caregiver is free for, offered to an Open Platform provider
-- (docs/section5/decisions.md, D7 and D16). No foreign keys outside report's own tables.

-- ---------------------------------------------------------------- providers, kept from ProviderUpdated ---

CREATE TABLE report_provider (
    provider_id       BIGINT       NOT NULL COMMENT 'notification''s provider',
    client_id         VARCHAR(64)  NULL COMMENT 'the OAuth2 client the job API''s token names; changes when the secret is rotated',
    organisation_name VARCHAR(200) NOT NULL,
    status            ENUM('ACTIVE','SUSPENDED') NOT NULL,
    version           INT          NOT NULL COMMENT 'ProviderUpdated.version: an older event changes nothing',
    PRIMARY KEY (provider_id),
    UNIQUE KEY uk_report_provider_client (client_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE report_provider_service (
    provider_id  BIGINT       NOT NULL,
    service_name VARCHAR(100) NOT NULL COMMENT 'a name in value_added_service',
    PRIMARY KEY (provider_id, service_name),
    KEY idx_report_provider_service_name (service_name),
    CONSTRAINT fk_report_provider_service FOREIGN KEY (provider_id) REFERENCES report_provider (provider_id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- -------------------------------------------------------------------------------------- provider jobs ---

CREATE TABLE report_provider_job (
    id                             BIGINT       NOT NULL AUTO_INCREMENT,
    provider_id                    BIGINT       NOT NULL,
    value_added_service_request_id BIGINT       NOT NULL COMMENT 'one job per request: a declined job goes to a manager, not to another provider',
    service_name                   VARCHAR(100) NOT NULL,
    district                       VARCHAR(2)   NOT NULL COMMENT 'Singapore postal district; empty when the postal code is unknown',
    area_name                      VARCHAR(100) NOT NULL,
    elder_summary                  VARCHAR(200) NOT NULL COMMENT 'what the provider decides on, without the elder''s name',
    languages                      VARCHAR(200) NOT NULL,
    booked_by                      VARCHAR(50)  NOT NULL,
    scheduled_start                DATETIME     NOT NULL,
    duration_minutes               INT          NOT NULL,
    status                         ENUM('NEW','ACCEPTED','DECLINED','COMPLETED','CANCELLED') NOT NULL,
    respond_by                     DATETIME     NOT NULL,
    dispatched_at                  DATETIME     NOT NULL,
    accepted_at                    DATETIME     NULL,
    declined_at                    DATETIME     NULL,
    decline_reason                 ENUM('OUTSIDE_HOURS','NO_PRACTITIONER','OUTSIDE_AREA','OTHER') NULL,
    decline_note                   VARCHAR(500) NULL,
    completed_at                   DATETIME     NULL,
    actual_start                   DATETIME     NULL,
    actual_end                     DATETIME     NULL,
    completion_note                VARCHAR(2000) NULL,
    cancelled_at                   DATETIME     NULL,
    cancel_reason                  VARCHAR(200) NULL,
    version                        INT          NOT NULL COMMENT 'ProviderJobUpdated.version, and the webhooks''',
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_provider_job_request (value_added_service_request_id),
    KEY idx_report_provider_job_provider (provider_id, status, dispatched_at),
    KEY idx_report_provider_job_due (status, respond_by),
    CONSTRAINT fk_report_provider_job_provider FOREIGN KEY (provider_id) REFERENCES report_provider (provider_id),
    CONSTRAINT fk_report_provider_job_request FOREIGN KEY (value_added_service_request_id)
        REFERENCES value_added_service_request (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The job API's Idempotency-Key: a key that succeeded answers the job as it stands, for 24 hours.
CREATE TABLE report_provider_job_key (
    provider_id     BIGINT      NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    job_id          BIGINT      NOT NULL,
    action          ENUM('ACCEPT','DECLINE','COMPLETE') NOT NULL,
    created_at      DATETIME    NOT NULL,
    PRIMARY KEY (provider_id, idempotency_key),
    KEY idx_report_provider_job_key_age (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
