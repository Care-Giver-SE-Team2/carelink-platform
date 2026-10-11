-- report's own record of the facts a weekly report is made from (docs/platform/event-catalogue.md,
-- section 5). report fills these tables from the events visit and core publish, one table per kind of
-- fact, and reads none of visit's or core's tables any more. Each row keeps the field its events are
-- ordered by - the visit's version, the timeline entry's id, the envelope's sequence - and an update
-- older than the row is ignored.
--
-- A platform migration while the services share one schema; at the schema split these tables move to
-- report's. V10 fills them once from the tables report used to read.

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
