-- notification's own database (docs/platform/service-boundaries.md, section 9). Its tables as they stood in
-- the shared schema when they moved out of core's: core's V2 created them, platform migration V7 added the
-- message's elder, and V3 dropped the subscription's foreign key to core's elder.
--
-- The events tables come with the platform's migrations (libs/events, db/platform), as in every service.

CREATE TABLE notification (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT        NOT NULL COMMENT 'core''s account the message is for',
    event_type        VARCHAR(40)   NOT NULL,
    channel           ENUM('IN_APP','SMS','EMAIL') NOT NULL DEFAULT 'IN_APP',
    title             VARCHAR(150)  NOT NULL,
    body              VARCHAR(1000) NULL,
    resource_type     VARCHAR(50)   NULL COMMENT 'what it links to: an incident, a roster change, a credential ...',
    resource_id       BIGINT        NULL,
    elder_id          BIGINT        NULL COMMENT 'the elder a message about care is about; null for one about the account',
    status            ENUM('PENDING','SENT','READ','FAILED') NOT NULL DEFAULT 'PENDING',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at           DATETIME      NULL,
    read_at           DATETIME      NULL,
    PRIMARY KEY (id),
    KEY idx_notification_recipient (recipient_user_id, status, created_at),
    KEY idx_notification_resource (resource_type, resource_id),
    KEY idx_notification_recipient_elder (recipient_user_id, elder_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE notification_subscription (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL COMMENT 'core''s account',
    elder_id   BIGINT      NULL COMMENT 'whose events, in core; null for events about the user themself, e.g. credential expiring',
    event_type VARCHAR(40) NOT NULL COMMENT 'VISIT_STARTED, VISIT_COMPLETED, INCIDENT_RAISED, INCIDENT_ESCALATED, CREDENTIAL_EXPIRING, SPOT_CHECK_REQUESTED, ROSTER_CHANGED',
    channel    ENUM('IN_APP','SMS','EMAIL') NOT NULL DEFAULT 'IN_APP',
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription (user_id, elder_id, event_type, channel)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Who read which inbox, written by the shared access audit (libs/shared); every service keeps its own
CREATE TABLE audit_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT       NULL COMMENT 'core''s account; null for scheduled jobs',
    action        VARCHAR(50)  NOT NULL COMMENT 'READ, CREATE, UPDATE, STATE_CHANGE, EXPORT ...',
    resource_type VARCHAR(50)  NOT NULL COMMENT 'table or aggregate name',
    resource_id   BIGINT       NULL,
    result        ENUM('OK','DENIED','FAILED') NOT NULL DEFAULT 'OK',
    detail        VARCHAR(500) NULL,
    occurred_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_resource (resource_type, resource_id, occurred_at),
    KEY idx_audit_actor (actor_user_id, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
