-- The elder a message about care is about. The event that asks for the message names the elder, and
-- the inbox's family rule checks it against the elders core says the reader may read, instead of
-- joining incident, roster_change, spot_check, value_added_service_request and care_plan, which other
-- services own. Messages kept before get their elder once, through those joins, while the schema is
-- still shared.
ALTER TABLE notification
    ADD COLUMN elder_id BIGINT NULL COMMENT 'the elder a message about care is about; null for one about the account'
        AFTER resource_id,
    ADD KEY idx_notification_recipient_elder (recipient_user_id, elder_id);

UPDATE notification n
LEFT JOIN incident i ON n.resource_type = 'INCIDENT' AND i.id = n.resource_id
LEFT JOIN roster_change rc ON n.resource_type = 'ROSTER_CHANGE' AND rc.id = n.resource_id
LEFT JOIN spot_check sc ON n.resource_type = 'SPOT_CHECK' AND sc.id = n.resource_id
LEFT JOIN value_added_service_request vasr ON n.resource_type = 'VALUE_ADDED_REQUEST' AND vasr.id = n.resource_id
LEFT JOIN care_plan cp ON n.resource_type = 'CARE_PLAN' AND cp.id = n.resource_id
SET n.elder_id = COALESCE(i.elder_id, rc.elder_id, sc.elder_id, vasr.elder_id, cp.elder_id)
WHERE n.elder_id IS NULL;
