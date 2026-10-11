-- Tests only (src/test/resources): the messages core asked the notification service to keep, read the
-- way the tests used to read the notification table. Each is a NotificationRequested event in core's
-- outbox; nothing delivers or reads it here, so every one is PENDING. Deleting from the view deletes the
-- events. Version 900, so it comes after every platform migration, and it never ships.
CREATE OR REPLACE VIEW requested_notification AS
SELECT o.id,
       JSON_VALUE(o.payload, '$.recipientUserId' RETURNING SIGNED) AS recipient_user_id,
       JSON_VALUE(o.payload, '$.kind')                                AS event_type,
       JSON_VALUE(o.payload, '$.channel')                             AS channel,
       JSON_VALUE(o.payload, '$.title' RETURNING CHAR(150))           AS title,
       JSON_VALUE(o.payload, '$.body' RETURNING CHAR(1000))           AS body,
       JSON_VALUE(o.payload, '$.resourceType')                        AS resource_type,
       JSON_VALUE(o.payload, '$.resourceId' RETURNING SIGNED)       AS resource_id,
       JSON_VALUE(o.payload, '$.elderId' RETURNING SIGNED)          AS elder_id,
       'PENDING'                                                      AS status,
       o.occurred_at                                                  AS created_at,
       CAST(NULL AS DATETIME)                                         AS sent_at,
       CAST(NULL AS DATETIME)                                         AS read_at
FROM outbox_event o
WHERE o.type = 'NotificationRequested';
