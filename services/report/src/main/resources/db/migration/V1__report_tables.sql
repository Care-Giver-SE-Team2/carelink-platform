-- report's own database (docs/platform/service-boundaries.md, section 9). Its tables as they stood in the
-- shared schema when they moved out of core's: core's V2, V8 and V19 created them, V19 and V22 changed them,
-- and platform migration V6 added a request's reminder. Platform migration V3 had dropped their foreign keys
-- to core's and visit's tables, so only the keys between report's own tables are here; the indexes MySQL had
-- made for the dropped keys stay. Then the facts report keeps from visit's and core's events, as platform
-- migration V9 made them (docs/platform/event-catalogue.md, section 5).
--
-- The events tables come with the platform's migrations (libs/events, db/platform), as in every service.

-- --------------------------------------------------------------------------------------- reports ---

CREATE TABLE report_basis (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    elder_id            BIGINT       NOT NULL COMMENT 'core''s elder',
    period_start        DATE         NOT NULL,
    period_end          DATE         NOT NULL,
    facts               JSON         NOT NULL COMMENT 'every fact the run read, before any reader''s filter; back office only',
    facts_version       SMALLINT     NOT NULL DEFAULT 1 COMMENT 'the shape of facts; raised whenever that shape changes',
    visits_planned      INT          NOT NULL DEFAULT 0 COMMENT 'visits in the period that were not cancelled',
    visits_completed    INT          NOT NULL DEFAULT 0 COMMENT 'of those, the ones carried out: COMPLETED, VERIFIED or AUTO_CLOSED',
    fulfilment_rate     DECIMAL(5,2) NULL     COMMENT 'visits_completed out of visits_planned, in percent; null when nothing was planned',
    vitals_out_of_range INT          NOT NULL DEFAULT 0 COMMENT 'readings flagged out of range when they were entered',
    incident_count      INT          NOT NULL DEFAULT 0,
    avg_elder_rating    DECIMAL(3,2) NULL     COMMENT 'mean of the elder''s ratings of the period''s visits (UC-EL01); null without any',
    rating_count        INT          NOT NULL DEFAULT 0 COMMENT 'how many ratings avg_elder_rating is the mean of',
    data_complete       BOOLEAN      NOT NULL COMMENT 'false while a visit of the period is not closed (UC-MG07 2a)',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_report_basis_elder (elder_id, period_start, period_end)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE report (
    id                   BIGINT   NOT NULL AUTO_INCREMENT,
    elder_id             BIGINT   NOT NULL COMMENT 'core''s elder',
    basis_id             BIGINT   NULL COMMENT 'the basis the report was assembled from; null before V19',
    generated_by_user_id BIGINT   NULL COMMENT 'core''s account',
    audience             ENUM('FAMILY','REGULATOR','INTERNAL') NOT NULL,
    period_start         DATE     NOT NULL,
    period_end           DATE     NOT NULL,
    status               ENUM('DRAFT','PUBLISHED','ARCHIVED') NOT NULL DEFAULT 'DRAFT',
    content              JSON     NULL COMMENT 'rendered sections, already filtered for the audience',
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_report_elder (elder_id, period_start),
    CONSTRAINT fk_report_basis FOREIGN KEY (basis_id) REFERENCES report_basis (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE report_amendment (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    report_id      BIGINT        NOT NULL,
    kind           ENUM('CORRECTION','FOLLOW_UP') NOT NULL DEFAULT 'CORRECTION',
    note           VARCHAR(1000) NOT NULL,
    author_user_id BIGINT        NOT NULL COMMENT 'core''s account; every correction is signed',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_report_amendment_report (report_id, created_at),
    CONSTRAINT fk_report_amendment_report FOREIGN KEY (report_id) REFERENCES report (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE caregiver_review (
    id                 BIGINT   NOT NULL AUTO_INCREMENT,
    family_member_id   BIGINT   NOT NULL COMMENT 'core''s family member',
    elder_id           BIGINT   NOT NULL COMMENT 'core''s elder',
    caregiver_id       BIGINT   NOT NULL COMMENT 'core''s caregiver',
    period_start       DATE     NOT NULL,
    period_end         DATE     NOT NULL,
    overall_rating     TINYINT  NOT NULL,
    punctuality_score  TINYINT  NULL,
    care_quality_score TINYINT  NULL,
    feedback_notes     TEXT     NULL,
    renewal_decision   ENUM('RENEW_CURRENT','REQUEST_CHANGE','CANCEL_SERVICE') NOT NULL DEFAULT 'RENEW_CURRENT',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_review_caregiver (caregiver_id),
    KEY idx_review_period (period_start, period_end),
    KEY idx_review_family (family_member_id),
    KEY idx_review_elder (elder_id),
    CONSTRAINT chk_review_rating CHECK (overall_rating BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------- value-added services ---

CREATE TABLE value_added_service (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(100) NOT NULL,
    description      TEXT         NULL,
    duration_minutes INT          NOT NULL DEFAULT 60 COMMENT 'length of the visit an approved request dispatches',
    status           ENUM('AVAILABLE','UNAVAILABLE') NOT NULL DEFAULT 'AVAILABLE',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_vas_duration CHECK (duration_minutes > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE value_added_service_request (
    id                            BIGINT      NOT NULL AUTO_INCREMENT,
    elder_id                      BIGINT      NOT NULL COMMENT 'core''s elder',
    value_added_service_id        BIGINT      NOT NULL,
    requested_by_family_member_id BIGINT      NULL COMMENT 'core''s family member; null when the elder requested it directly',
    approving_family_member_id    BIGINT      NULL COMMENT 'core''s family member',
    visit_id                      BIGINT      NULL COMMENT 'visit''s visit, set once dispatched as one',
    requested_schedule            DATETIME    NULL,
    special_instructions          TEXT        NULL,
    status                        ENUM('PENDING_APPROVAL','APPROVED','REJECTED','DISPATCHED','COMPLETED','CANCELLED')
                                  NOT NULL DEFAULT 'PENDING_APPROVAL',
    decided_at                    DATETIME    NULL,
    created_at                    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    family_reminded_at            DATETIME(6) NULL COMMENT 'when the family was reminded to answer; a request is reminded once',
    PRIMARY KEY (id),
    KEY idx_vas_request_elder (elder_id, status),
    KEY idx_vas_request_visit (visit_id),
    CONSTRAINT fk_vas_request_service FOREIGN KEY (value_added_service_id) REFERENCES value_added_service (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------- the facts kept from visit's and core's events ---

CREATE TABLE report_visit (
    visit_id                BIGINT      NOT NULL,
    elder_id                BIGINT      NOT NULL,
    scheduled_start         DATETIME    NOT NULL,
    -- From VisitScheduled only: they never change after it, and stay empty until it arrives
    scheduled_end           DATETIME    NULL,
    service_type            VARCHAR(50) NULL,
    care_plan_id            BIGINT      NULL,
    care_plan_node_id       BIGINT      NULL,
    -- The state the visit's latest event left
    status                  VARCHAR(20) NOT NULL,
    caregiver_id            BIGINT      NULL,
    checked_in_at           DATETIME    NULL,
    checked_out_at          DATETIME    NULL,
    version                 INT         NOT NULL,
    -- Copied at the first deployment; no event carries evidence yet (VisitEvidenceUpdated is reserved)
    evidence_count          INT         NOT NULL DEFAULT 0,
    verified_evidence_count INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (visit_id),
    KEY idx_report_visit_elder (elder_id, scheduled_start),
    KEY idx_report_visit_start (scheduled_start)
);

-- A task's recorded result: the caregiver's notes are the report's observations
CREATE TABLE report_visit_task (
    task_id               BIGINT       NOT NULL,
    visit_id              BIGINT       NOT NULL,
    elder_id              BIGINT       NOT NULL,
    visit_scheduled_start DATETIME     NOT NULL,
    name                  VARCHAR(150) NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    caregiver_note        VARCHAR(500) NULL,
    recorded_at           DATETIME     NULL,
    sequence              BIGINT       NOT NULL,
    PRIMARY KEY (task_id),
    KEY idx_report_visit_task_elder (elder_id, visit_scheduled_start)
);

-- One row per reading. A set of readings arrives once (VitalsRecorded); readings kept before health
-- records existed have no health_record_id and are copied by their own id
CREATE TABLE report_vital_sign (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    health_record_id      BIGINT       NULL,
    source_id             BIGINT       NULL COMMENT 'vital_sign.id, for a reading copied at the first deployment',
    visit_id              BIGINT       NOT NULL,
    elder_id              BIGINT       NOT NULL,
    visit_scheduled_start DATETIME     NOT NULL,
    metric                VARCHAR(50)  NOT NULL,
    value                 DECIMAL(8,2) NOT NULL,
    unit                  VARCHAR(20)  NULL,
    out_of_range          BOOLEAN      NOT NULL,
    recorded_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_vital_sign_reading (health_record_id, metric),
    UNIQUE KEY uk_report_vital_sign_source (source_id),
    KEY idx_report_vital_sign_elder (elder_id, visit_scheduled_start)
);

-- The elder's one answer about a visit
CREATE TABLE report_elder_confirmation (
    visit_id              BIGINT      NOT NULL,
    elder_id              BIGINT      NOT NULL,
    visit_scheduled_start DATETIME    NOT NULL,
    confirmation_status   VARCHAR(20) NOT NULL,
    rating                TINYINT     NULL,
    comment               TEXT        NULL,
    confirmed_at          DATETIME    NOT NULL,
    PRIMARY KEY (visit_id),
    KEY idx_report_elder_confirmation_elder (elder_id, visit_scheduled_start)
);

-- An incident as its latest timeline entry leaves it. IncidentRaised fills in what it alone carries;
-- until it arrives reported_at is empty, and the incident is in no week
CREATE TABLE report_incident (
    incident_id BIGINT        NOT NULL,
    elder_id    BIGINT        NOT NULL,
    visit_id    BIGINT        NULL,
    category    VARCHAR(20)   NULL,
    description VARCHAR(2000) NULL,
    reported_at DATETIME      NULL,
    severity    VARCHAR(10)   NULL,
    status      VARCHAR(30)   NULL,
    resolved_at DATETIME      NULL,
    last_log_id BIGINT        NULL COMMENT 'the timeline entry the status, severity and resolution come from',
    PRIMARY KEY (incident_id),
    KEY idx_report_incident_elder (elder_id, reported_at)
);

CREATE TABLE report_incident_log (
    log_id      BIGINT       NOT NULL,
    incident_id BIGINT       NOT NULL,
    actor       VARCHAR(150) NOT NULL,
    action      VARCHAR(255) NOT NULL,
    detail      VARCHAR(500) NULL,
    occurred_at DATETIME     NOT NULL,
    PRIMARY KEY (log_id),
    KEY idx_report_incident_log_incident (incident_id, occurred_at)
);

CREATE TABLE report_spot_check (
    spot_check_id      BIGINT       NOT NULL,
    elder_id           BIGINT       NOT NULL,
    visit_id           BIGINT       NULL,
    caregiver_id       BIGINT       NULL,
    proposed_time      DATETIME     NOT NULL,
    approval_status    VARCHAR(20)  NOT NULL,
    result             VARCHAR(20)  NULL,
    outcome            VARCHAR(20)  NULL,
    finding            VARCHAR(500) NULL,
    caregiver_response VARCHAR(500) NULL,
    checked_at         DATETIME     NULL,
    sequence           BIGINT       NOT NULL,
    PRIMARY KEY (spot_check_id),
    KEY idx_report_spot_check_elder (elder_id, proposed_time)
);

CREATE TABLE report_roster_change (
    roster_change_id      BIGINT      NOT NULL,
    visit_id              BIGINT      NOT NULL,
    elder_id              BIGINT      NOT NULL,
    visit_start           DATETIME    NOT NULL,
    original_caregiver_id BIGINT      NULL,
    status                VARCHAR(20) NOT NULL,
    outcome               VARCHAR(20) NULL,
    decided_by            VARCHAR(20) NULL,
    assigned_caregiver_id BIGINT      NULL,
    sequence              BIGINT      NOT NULL,
    PRIMARY KEY (roster_change_id),
    KEY idx_report_roster_change_elder (elder_id, visit_start),
    KEY idx_report_roster_change_visit (visit_id)
);

-- -------------------------------------------------------------------------------- every service ---

-- Who read which report, written by the shared access audit (libs/shared); every service keeps its own
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

-- The weekly reports and the settlement of value-added services run on one replica at a time
-- (report/infrastructure/config/SchedulerLocks)
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
