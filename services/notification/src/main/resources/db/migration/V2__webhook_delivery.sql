-- The Open Platform's webhooks (docs/api/webhooks.md): where each provider takes them, and every delivery
-- CareLink makes, with each attempt. The provider's own record (its application, approval and suspension)
-- comes with onboarding; provider_id is the id it gets there.
--
-- Signing secrets are kept encrypted (AES-256-GCM), never as themselves; the key is the service's setting
-- carelink.webhooks.encryption-key, outside the database and git.

CREATE TABLE provider_webhook_endpoint (
    provider_id             BIGINT        NOT NULL,
    url                     VARCHAR(2048) NOT NULL COMMENT 'HTTPS; the deliveries created from now on go here',
    events                  VARCHAR(200)  NOT NULL COMMENT 'the events subscribed to, comma-separated: job.dispatched,job.cancelled',
    signing_secret          VARCHAR(512)  NOT NULL COMMENT 'the signing secret, encrypted',
    previous_signing_secret VARCHAR(512)  NULL COMMENT 'the secret before the last rotation, encrypted; signed with too until previous_secret_until',
    previous_secret_until   DATETIME(6)   NULL,
    enabled                 BOOLEAN       NOT NULL DEFAULT TRUE COMMENT 'false while the provider is suspended: nothing is sent',
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    PRIMARY KEY (provider_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- One webhook, as every attempt sends it: the body is kept so that each attempt repeats the same bytes.
CREATE TABLE webhook_delivery (
    id              VARCHAR(40)   NOT NULL COMMENT 'the delivery ID, dlv_...: the same on every attempt and resend',
    provider_id     BIGINT        NOT NULL,
    event_type      VARCHAR(40)   NOT NULL COMMENT 'job.dispatched, job.cancelled or ping',
    job_id          VARCHAR(40)   NULL COMMENT 'the job the event is about; null for ping',
    dedupe_key      VARCHAR(120)  NULL COMMENT 'what caused it, e.g. the event handled: the same cause makes one delivery',
    url             VARCHAR(2048) NOT NULL COMMENT 'the endpoint when the delivery was created',
    body            MEDIUMTEXT    NOT NULL,
    status          ENUM('PENDING','DELIVERED','FAILED') NOT NULL COMMENT 'PENDING is RETRYING to the provider',
    attempt_count   INT           NOT NULL DEFAULT 0,
    max_attempts    INT           NOT NULL COMMENT 'the retry schedule''s six, one more for each resend',
    next_attempt_at DATETIME(6)   NULL COMMENT 'when PENDING; null otherwise',
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_webhook_delivery_dedupe (dedupe_key),
    KEY idx_webhook_delivery_due (status, next_attempt_at),
    KEY idx_webhook_delivery_provider (provider_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE webhook_delivery_attempt (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    delivery_id     VARCHAR(40)  NOT NULL,
    attempt_no      INT          NOT NULL COMMENT 'from 1',
    attempted_at    DATETIME(6)  NOT NULL,
    response_status INT          NULL COMMENT 'the HTTP status answered; null when there was no answer',
    duration_ms     INT          NOT NULL,
    error           VARCHAR(40)  NULL COMMENT 'why there was no answer: TIMEOUT, CONNECTION_FAILED, TLS_ERROR, ...',
    PRIMARY KEY (id),
    UNIQUE KEY uk_webhook_attempt (delivery_id, attempt_no),
    CONSTRAINT fk_webhook_attempt_delivery FOREIGN KEY (delivery_id) REFERENCES webhook_delivery (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- notification's first scheduled job (the webhook attempts) runs on one replica at a time
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
