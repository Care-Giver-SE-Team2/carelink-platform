-- UC-MG04's changes to visits, kept as the steps of a saga (docs/platform/service-boundaries.md, section 7).
-- Re-rostering decides in core's database and visit changes the visit in its own, so each change visit is
-- asked to make is written down here first, in the transaction that decides it, with the request key it is
-- sent with. visit is called after that transaction commits; a call that does not get through is sent
-- again, with the same key, until visit answers or the retries run out. Only once visit has made the change
-- does core settle the roster change and tell people.
--
--   PENDING   decided, not yet made: the step holds its visit, so nothing else is decided for it
--   DONE      visit made the change and core has settled what follows
--   REJECTED  visit refused (the visit had started, been called off or moved, or did not exist); nothing
--             was changed, the visit is free for the next decision, and the person who decided is told
--   ABANDONED visit could not be reached in time; a manager has an incident, the absence cannot be
--             confirmed, and the manager's next re-roster sends the step again with its key
CREATE TABLE visit_change_step (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    absence_id             BIGINT       NOT NULL,
    roster_change_id       BIGINT       NULL COMMENT 'the change the step settles; null until settled when the change is only recorded then (a visit left uncovered, a cover carried on)',
    -- The visit as the decision saw it
    visit_id               BIGINT       NOT NULL COMMENT 'visit''s visit',
    elder_id               BIGINT       NOT NULL,
    care_plan_id           BIGINT       NULL,
    service_type           VARCHAR(50)  NULL,
    visit_start            DATETIME     NOT NULL,
    visit_end              DATETIME     NULL,
    original_caregiver_id  BIGINT       NULL COMMENT 'the absent caregiver',
    -- What visit is asked to do
    kind                   ENUM('REASSIGN','MARK_UNCOVERED','CALL_OFF','MOVE') NOT NULL,
    to_caregiver_id        BIGINT       NULL,
    new_start              DATETIME     NULL COMMENT 'a move''s new time',
    by_user_id             BIGINT       NULL COMMENT 'who visit''s assignment history names',
    rostering_candidate_id BIGINT       NULL,
    reason                 VARCHAR(500) NULL COMMENT 'visit''s assignment history reason',
    -- What core does once visit has made it
    rostering_run_id       BIGINT       NULL,
    decided_by             ENUM('FAMILY','DEFAULT_PLAN','MANAGER') NULL,
    decided_by_user_id     BIGINT       NULL COMMENT 'who decided, and is told if visit refuses later; null when the system did',
    caregiver_name         VARCHAR(150) NULL COMMENT 'who the family is told is coming',
    note                   VARCHAR(255) NULL COMMENT 'roster_change.note once settled',
    -- Progress
    request_key            VARCHAR(120) NOT NULL COMMENT 'sent with every try (Idempotency-Key)',
    status                 ENUM('PENDING','DONE','REJECTED','ABANDONED') NOT NULL,
    attempts               INT          NOT NULL DEFAULT 0 COMMENT 'tries that did not get through',
    next_attempt_at        DATETIME     NOT NULL COMMENT 'when the sweep tries a PENDING step again',
    last_error             VARCHAR(500) NULL,
    result_visit_id        BIGINT       NULL COMMENT 'the visit a move created',
    incident_id            BIGINT       NULL COMMENT 'the incident a manager got when the step was abandoned',
    created_at             DATETIME     NOT NULL,
    updated_at             DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_visit_change_step_request (request_key),
    KEY idx_visit_change_step_due (status, next_attempt_at),
    KEY idx_visit_change_step_absence (absence_id, status),
    CONSTRAINT fk_visit_change_step_absence FOREIGN KEY (absence_id) REFERENCES absence_report (id),
    CONSTRAINT fk_visit_change_step_change FOREIGN KEY (roster_change_id) REFERENCES roster_change (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
