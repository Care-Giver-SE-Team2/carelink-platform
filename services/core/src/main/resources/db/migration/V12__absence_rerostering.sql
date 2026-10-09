-- UC-MG04: re-roster on caregiver absence.
--
-- An approved absence vacates the visits its caregiver was booked on. Each vacated visit
-- gets one roster_change row, and the row is the record the use case's rules ask for:
-- what the engine proposed, whether the family chose or the institution's default plan ran
-- when they did not answer in time, and how the visit ended up - with another caregiver, at
-- another time, skipped, or uncovered with an incident raised for a manager. No vacated
-- visit can disappear without one.
--
-- Times are written by the application from its own clock, like every other column JPA
-- writes, so created_at and updated_at carry no database default.
CREATE TABLE roster_change (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    absence_id            BIGINT       NOT NULL,
    visit_id              BIGINT       NOT NULL COMMENT 'the vacated visit',
    elder_id              BIGINT       NOT NULL,
    original_caregiver_id BIGINT       NOT NULL COMMENT 'the absent caregiver',
    visit_start           DATETIME     NOT NULL COMMENT 'when the vacated visit was due, as it was offered to the family',
    visit_end             DATETIME     NULL,
    rostering_run_id      BIGINT       NULL COMMENT 'the run whose shortlist this change offers or was settled from',
    proposed_caregiver_id BIGINT       NULL COMMENT 'the default plan: the best replacement; null when nobody was free',
    status                ENUM('AWAITING_FAMILY','UNCOVERED','RESOLVED') NOT NULL,
    outcome               ENUM('REPLACED','RESCHEDULED','SKIPPED','WITHDRAWN') NULL COMMENT 'set once RESOLVED; WITHDRAWN when the visit was called off elsewhere first',
    decided_by            ENUM('FAMILY','DEFAULT_PLAN','MANAGER') NULL COMMENT 'a default plan is told apart from a choice',
    decided_by_user_id    BIGINT       NULL COMMENT 'soft FK to app_user.id; null when the default plan ran',
    assigned_caregiver_id BIGINT       NULL COMMENT 'who covers the visit now, when REPLACED or RESCHEDULED',
    rescheduled_visit_id  BIGINT       NULL COMMENT 'the visit created at the new time, when RESCHEDULED',
    incident_id           BIGINT       NULL COMMENT 'raised when nobody was free to cover the visit',
    respond_by            DATETIME     NULL COMMENT 'the family answers by then; afterwards the default plan runs',
    decided_at            DATETIME     NULL,
    note                  VARCHAR(255) NULL COMMENT 'why it was settled this way',
    created_at            DATETIME     NOT NULL,
    updated_at            DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roster_change_absence_visit (absence_id, visit_id),
    KEY idx_roster_change_elder (elder_id, status),
    KEY idx_roster_change_deadline (status, respond_by),
    CONSTRAINT fk_roster_change_absence FOREIGN KEY (absence_id) REFERENCES absence_report (id),
    CONSTRAINT fk_roster_change_visit   FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Step 7: the manager confirms every vacated visit is accounted for.
ALTER TABLE absence_report
    ADD COLUMN coverage_confirmed_at         DATETIME NULL COMMENT 'UC-MG04 step 7' AFTER status,
    ADD COLUMN coverage_confirmed_by_user_id BIGINT   NULL COMMENT 'soft FK to app_user.id' AFTER coverage_confirmed_at;

-- The match reason the manager and the family are shown next to each suggestion.
ALTER TABLE rostering_candidate
    ADD COLUMN match_reason VARCHAR(120) NULL COMMENT 'why this candidate ranks where it does, in words' AFTER excluded_by_code;

-- The rule set the replacement search runs. HARD rules exclude a candidate, SOFT rules only
-- move the score; switching one off or changing a threshold is a row update, not a release.
-- Every rule runs for every candidate and every result is kept, so "why was she not
-- suggested" always has an answer.
INSERT INTO rostering_constraint (code, name, kind, parameter_value, enabled) VALUES
    ('NOT_ON_LEAVE',        'Not on approved leave that day',                'HARD', NULL,  TRUE),
    ('CERTIFICATION_VALID', 'Holds every certificate the care plan requires', 'HARD', NULL,  TRUE),
    ('NO_TIME_CLASH',       'Free at that time',                              'HARD', NULL,  TRUE),
    ('DAILY_VISIT_CAP',     'Visits in one day',                              'HARD', '8',   TRUE),
    ('DAILY_HOURS_CAP',     'Hours of visits in one day',                     'HARD', '8.0', TRUE),
    ('CONTINUITY',          'Has cared for this elder before',                'SOFT', NULL,  TRUE),
    ('SECTOR_BAND',         'Works in the elder''s sector',                   'SOFT', NULL,  TRUE),
    ('DIALECT_MATCH',       'Speaks one of the elder''s dialects',            'SOFT', NULL,  TRUE),
    ('SPOT_CHECK',          'Recent spot-check conclusions, days looked back', 'SOFT', '90',  TRUE);
