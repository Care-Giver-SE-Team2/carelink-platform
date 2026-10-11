-- When the family was reminded to answer a value-added service request, once per request. It used to
-- be read from the notification table, which is the notification service's; report now notes it on the
-- request when it sends the reminder, and sends none for a request that has one. Requests reminded
-- before keep that time, taken once from the reminders already sent.
ALTER TABLE value_added_service_request
    ADD COLUMN family_reminded_at DATETIME(6) NULL
        COMMENT 'when the family was reminded to answer; a request is reminded once';

UPDATE value_added_service_request r
SET r.family_reminded_at = (
    SELECT MIN(n.created_at) FROM notification n
    WHERE n.event_type = 'VALUE_ADDED_REMINDER' AND n.resource_type = 'VALUE_ADDED_REQUEST'
      AND n.resource_id = r.id);
