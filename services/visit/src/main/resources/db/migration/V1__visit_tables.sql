-- visit's own database (docs/platform/service-boundaries.md, section 9). Its tables as they stood in the
-- shared schema when they moved out of core's: created by core's V2, V16, V17, V18 and V20 and changed by
-- V17, V18, V20 and V23. Platform migration V3 had dropped their foreign keys to core's tables, so only
-- the keys between visit's own tables are here; the indexes MySQL had made for the dropped keys stay.
--
-- The events tables come with the platform's migrations (libs/events, db/platform), as in every service.

CREATE TABLE visit (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    elder_id          BIGINT        NOT NULL COMMENT 'core''s elder',
    caregiver_id      BIGINT        NULL COMMENT 'core''s caregiver; null while unassigned',
    care_plan_node_id BIGINT        NULL COMMENT 'core''s care plan task this fulfils',
    absence_id        BIGINT        NULL COMMENT 'core''s absence, when this visit was re-rostered because of one',
    service_type      VARCHAR(50)   NULL,
    scheduled_start   DATETIME      NOT NULL,
    scheduled_end     DATETIME      NULL,
    checked_in_at     DATETIME      NULL,
    checked_out_at    DATETIME      NULL,
    status            ENUM('SCHEDULED','ARRIVED','IN_PROGRESS','COMPLETED','VERIFIED','AUTO_CLOSED','EXCEPTION','CANCELLED')
                      NOT NULL DEFAULT 'SCHEDULED'
                      COMMENT 'COMPLETED = checked out, awaiting the elder; VERIFIED = elder confirmed and supervisor verified; AUTO_CLOSED = the elder never answered (DECISION 16)',
    state_deadline    DATETIME      NULL COMMENT 'when the current state must have advanced by; SYS03 infers a missed check-in from it',
    care_plan_id      BIGINT        NULL COMMENT 'core''s care plan VERSION in force when this visit was created (story A6). Superseded versions are never edited, so pointing at the row is the snapshot',
    version           INT           NOT NULL DEFAULT 0 COMMENT 'optimistic lock for concurrent state changes',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    health_flag       VARCHAR(32)   NULL,
    health_note       VARCHAR(1000) NULL,
    instructions      VARCHAR(1000) NULL COMMENT 'special instructions for the caregiver, e.g. from an extra-service request',
    PRIMARY KEY (id),
    KEY idx_visit_elder (elder_id, scheduled_start),
    KEY idx_visit_caregiver (caregiver_id, scheduled_start),
    KEY idx_visit_status (status),
    KEY idx_visit_deadline (status, state_deadline),
    KEY idx_visit_absence (absence_id),
    KEY ix_visit_missed_check_in_scan (status, scheduled_start, id),
    CONSTRAINT chk_visit_health_flag CHECK
        (health_flag IS NULL OR health_flag IN ('NO_CONCERN','ATTENTION','MEDICAL_REVIEW'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE visit_assignment (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    visit_id               BIGINT       NOT NULL,
    caregiver_id           BIGINT       NOT NULL COMMENT 'core''s caregiver',
    assigned_by_user_id    BIGINT       NULL COMMENT 'core''s account',
    status                 ENUM('ACTIVE','REPLACED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    reason                 VARCHAR(255) NULL COMMENT 'why this assignment was made or replaced',
    assigned_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at               DATETIME     NULL,
    rostering_candidate_id BIGINT       NULL COMMENT 'core''s rostering candidate: the run and option this assignment came from; null when assigned by hand',
    PRIMARY KEY (id),
    KEY idx_assignment_visit (visit_id, status),
    KEY idx_assignment_caregiver (caregiver_id, assigned_at),
    KEY idx_assignment_candidate (rostering_candidate_id),
    CONSTRAINT fk_assignment_visit FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE visit_state_transition (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    visit_id         BIGINT       NOT NULL,
    from_state       VARCHAR(20)  NOT NULL,
    to_state         VARCHAR(20)  NOT NULL,
    actor_user_id    BIGINT       NULL COMMENT 'core''s account; null when the system acted',
    result           ENUM('APPLIED','REJECTED') NOT NULL,
    rejection_reason VARCHAR(255) NULL,
    occurred_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_transition_visit (visit_id, occurred_at),
    CONSTRAINT fk_transition_visit FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE visit_task (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    visit_id          BIGINT       NOT NULL,
    care_plan_node_id BIGINT       NULL COMMENT 'core''s care plan task this executes',
    name              VARCHAR(150) NOT NULL,
    status            ENUM('PENDING','DONE','SKIPPED','REFUSED') NOT NULL DEFAULT 'PENDING',
    outcome           VARCHAR(255) NULL,
    caregiver_note    VARCHAR(500) NULL,
    completed_at      DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_visit_task_visit (visit_id),
    UNIQUE KEY uk_visit_task_node (visit_id, care_plan_node_id),
    CONSTRAINT fk_visit_task_visit FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE visit_evidence (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    visit_id            BIGINT       NOT NULL,
    kind                ENUM('PHOTO','SIGNATURE','CHECKLIST','READING','NOTE') NOT NULL,
    reference           VARCHAR(255) NOT NULL COMMENT 'stored file reference',
    verification_status ENUM('UNVERIFIED','VERIFIED','REJECTED') NOT NULL DEFAULT 'UNVERIFIED',
    captured_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_evidence_visit (visit_id),
    CONSTRAINT fk_evidence_visit FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE visit_health_record (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    visit_id            BIGINT        NOT NULL,
    recorded_by_user_id BIGINT        NOT NULL COMMENT 'core''s account',
    health_flag         VARCHAR(32)   NOT NULL,
    health_note         VARCHAR(1000) NULL,
    recorded_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_health_record_visit (id, visit_id),
    KEY idx_health_record_history (visit_id, recorded_at, id),
    CONSTRAINT fk_health_record_visit FOREIGN KEY (visit_id) REFERENCES visit (id),
    CONSTRAINT chk_health_record_flag CHECK
        (health_flag IN ('NO_CONCERN','ATTENTION','MEDICAL_REVIEW'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE vital_sign (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    visit_id         BIGINT       NOT NULL,
    metric           VARCHAR(50)  NOT NULL COMMENT 'systolic, diastolic, pulse, temperature ...',
    value            DECIMAL(8,2) NOT NULL,
    unit             VARCHAR(20)  NULL,
    out_of_range     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'story C6: highlighted at entry time',
    recorded_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    health_record_id BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_vital_visit (visit_id),
    UNIQUE KEY uq_health_record_metric (health_record_id, metric),
    CONSTRAINT fk_vital_visit FOREIGN KEY (visit_id) REFERENCES visit (id),
    CONSTRAINT fk_vital_health_record FOREIGN KEY (health_record_id, visit_id)
        REFERENCES visit_health_record (id, visit_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE elder_confirmation (
    id                  BIGINT   NOT NULL AUTO_INCREMENT,
    visit_id            BIGINT   NOT NULL,
    elder_id            BIGINT   NOT NULL COMMENT 'core''s elder who answered; must equal visit.elder_id',
    confirmation_status ENUM('CONFIRMED','DISPUTED') NOT NULL,
    rating              TINYINT  NULL COMMENT 'EL01: the elder rates this one visit',
    comment             TEXT     NULL,
    confirmed_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_elder_confirmation_visit (visit_id),
    KEY idx_elder_confirmation_elder (elder_id),
    CONSTRAINT chk_elder_confirmation_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT fk_elder_confirmation_visit FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE caregiver_command_receipt (
    actor_user_id     BIGINT      NOT NULL,
    client_request_id CHAR(36)    NOT NULL,
    action_code       VARCHAR(30) NOT NULL,
    visit_id          BIGINT      NOT NULL,
    payload_hash      CHAR(64)    NOT NULL,
    result_id         BIGINT      NOT NULL,
    visit_version     INT         NOT NULL,
    occurred_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (actor_user_id, client_request_id),
    KEY idx_cg_command_result (actor_user_id, action_code, result_id),
    CONSTRAINT fk_cg_command_visit FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE visit_check_in_record (
    visit_id           BIGINT        NOT NULL PRIMARY KEY,
    actor_user_id      BIGINT        NOT NULL,
    client_request_id  CHAR(36)      NOT NULL,
    location_source    VARCHAR(30)   NOT NULL,
    latitude           DECIMAL(10,7) NULL,
    longitude          DECIMAL(10,7) NULL,
    accuracy           DOUBLE        NULL,
    location_note      VARCHAR(500)  NULL,
    client_captured_at VARCHAR(40)   NULL,
    received_at        DATETIME(6)   NOT NULL,
    CONSTRAINT fk_check_in_visit FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE visit_missed_check_in_trigger (
    visit_id               BIGINT      NOT NULL PRIMARY KEY,
    incident_id            BIGINT      NOT NULL UNIQUE COMMENT 'core''s incident the missed check-in raised',
    triggered_caregiver_id BIGINT      NOT NULL COMMENT 'core''s caregiver',
    scheduled_start        DATETIME(6) NOT NULL,
    check_in_due_at        DATETIME(6) NOT NULL,
    observed_visit_version INT         NOT NULL,
    triggered_at           DATETIME(6) NOT NULL,
    KEY idx_missed_check_in_caregiver (triggered_caregiver_id),
    CONSTRAINT fk_missed_check_in_visit FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Who read what and which commands were refused, written by the shared access audit (libs/shared)
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

-- The missed check-in scan runs on one replica at a time (visit/infrastructure/config/SchedulerLocks)
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
