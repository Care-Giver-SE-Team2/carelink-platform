-- Adds a terminal STOPPED status for care_plan (screen 1n, "Stop care plan"): a manager can
-- end an elder's active plan early. The plan row and its node tree are kept as-is — nothing is
-- deleted — only the four columns below record that it happened and why.
ALTER TABLE care_plan
    MODIFY COLUMN status ENUM('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'STOPPED') NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN stop_effective_date DATE     NULL COMMENT 'from this date the plan is no longer active',
    ADD COLUMN stop_reason          VARCHAR(500) NULL,
    ADD COLUMN stopped_by_user_id   BIGINT   NULL COMMENT 'soft FK to app_user.id',
    ADD COLUMN stopped_at           DATETIME NULL;
