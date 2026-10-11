-- notification has a database of its own (docs/platform/service-boundaries.md, section 9), built by its own
-- migrations, so its tables leave core's. Nothing in core reads or writes them: every message is a
-- NotificationRequested event, and the inbox is notification's.
--
-- The schema split starts from empty service databases; rows kept here before are not carried over.
DROP TABLE notification_subscription;
DROP TABLE notification;
