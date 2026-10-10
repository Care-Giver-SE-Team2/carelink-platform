-- A manager can decline a family's service application, with a reason the family sees. Whether an
-- application has been planned is not stored: it is worked out from the elder's care plan versions,
-- which never change once published. Declining is a person's decision, so it is the one outcome
-- recorded here; status becomes 'DECLINED' (the column is a plain VARCHAR, so no type change).
ALTER TABLE care_service_application
    ADD COLUMN declined_at         DATETIME(6)  NULL COMMENT 'UTC; set when a manager declines the application',
    ADD COLUMN declined_by_user_id BIGINT       NULL COMMENT 'soft FK to app_user.id: the manager who declined it',
    ADD COLUMN decline_reason      VARCHAR(255) NULL COMMENT 'shown to the family';
