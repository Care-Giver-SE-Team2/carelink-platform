-- A family member's FM05 message is a NotificationRequested event now, written in the transaction that
-- opens their response window, and the notification service keeps it. core no longer has the message's
-- row id, so a window opened from now on leaves first_notification_id empty. Windows opened before keep
-- the id they have. Nothing reads the column.
ALTER TABLE family_alert_window
    MODIFY COLUMN first_notification_id BIGINT NULL
        COMMENT 'the first message''s notification row, for windows opened before messages became events';
