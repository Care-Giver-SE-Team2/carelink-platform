-- Removing a provider (docs/section5/decisions.md, D19): REMOVED is for good. The row stays, for the jobs and
-- webhook deliveries that name it, and its email may apply again with the same portal user, so a portal user
-- may now have a removed provider and a new one. Only one of them may be not removed.

ALTER TABLE provider
    MODIFY status ENUM('ACTIVE','SUSPENDED','REMOVED') NOT NULL,
    ADD COLUMN current_portal_user_id VARCHAR(128)
        AS (IF(status = 'REMOVED', NULL, portal_user_id)) STORED
        COMMENT 'portal_user_id while not removed: one such provider per portal user',
    DROP INDEX uk_provider_portal_user,
    ADD KEY idx_provider_portal_user (portal_user_id, approved_at),
    ADD UNIQUE KEY uk_provider_current_portal_user (current_portal_user_id);
