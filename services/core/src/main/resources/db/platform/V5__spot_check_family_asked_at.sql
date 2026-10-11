-- When the family was last asked about a spot check: the request, and each reminder since. The
-- reminders read it to decide whom to ask again. It used to be read from the notification table,
-- which is the notification service's; the spot check alert now notes it on the check when it asks.
-- Checks asked before keep their last ask, taken once from the messages already sent.
ALTER TABLE spot_check
    ADD COLUMN family_asked_at DATETIME(6) NULL
        COMMENT 'when the family was last asked to agree: the request or the latest reminder; null if nobody was asked';

UPDATE spot_check s
SET s.family_asked_at = (
    SELECT MAX(n.created_at) FROM notification n
    WHERE n.resource_type = 'SPOT_CHECK' AND n.resource_id = s.id
      AND n.event_type IN ('SPOT_CHECK_REQUESTED', 'SPOT_CHECK_REMINDER'));
