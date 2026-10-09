-- FM05-owned event outcomes, IN_APP deduplication and personal response windows.
-- Identifiers are soft references, like audit_log: invalid events must remain diagnosable.
-- Existing notifications, incident receipts and other producers remain unchanged.
CREATE TABLE family_alert_event (
    event_id CHAR(36) NOT NULL PRIMARY KEY,
    event_type VARCHAR(40) NOT NULL,
    incident_id BIGINT NOT NULL,
    elder_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    state VARCHAR(24) NOT NULL,
    reason VARCHAR(64) NULL,
    last_attempt_at DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE family_alert_delivery (
    event_id CHAR(36) NOT NULL,
    family_member_id BIGINT NOT NULL,
    recipient_user_id BIGINT NULL,
    status VARCHAR(24) NOT NULL,
    reason VARCHAR(64) NULL,
    notification_id BIGINT NULL,
    attempted_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id, family_member_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE family_alert_window (
    incident_id BIGINT NOT NULL,
    family_member_id BIGINT NOT NULL,
    first_notification_id BIGINT NOT NULL,
    opened_at DATETIME(6) NOT NULL,
    acknowledge_by DATETIME(6) NOT NULL,
    PRIMARY KEY (incident_id, family_member_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
