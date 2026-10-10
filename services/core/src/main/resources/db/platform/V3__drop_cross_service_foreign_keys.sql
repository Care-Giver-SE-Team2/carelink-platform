-- The foreign keys between tables that different services own (docs/platform/service-boundaries.md,
-- sections 1 and 2). They go now, before the schema split, so that every service is built on a
-- database where no key crosses a service, the way it will run after the split: a service checks a
-- reference to another service's record in its code, through CoreApi or VisitApi.
--
-- The columns keep their ids, and the indexes MySQL created for the keys stay, so lookups by these
-- columns are as fast as before. No key had a cascading delete. CrossServiceForeignKeysIT fails the
-- build if such a key appears again, for example from an application migration an upstream sync
-- brings in; drop it in the next platform migration.

-- visit's tables -> core's
ALTER TABLE visit
    DROP FOREIGN KEY fk_visit_elder,
    DROP FOREIGN KEY fk_visit_caregiver,
    DROP FOREIGN KEY fk_visit_absence;
ALTER TABLE visit_assignment DROP FOREIGN KEY fk_assignment_caregiver;
ALTER TABLE elder_confirmation DROP FOREIGN KEY fk_elder_confirmation_elder;
ALTER TABLE visit_missed_check_in_trigger
    DROP FOREIGN KEY fk_missed_check_in_incident,
    DROP FOREIGN KEY fk_missed_check_in_caregiver;

-- core's tables -> visit's
ALTER TABLE rostering_candidate DROP FOREIGN KEY fk_candidate_visit;
ALTER TABLE roster_change DROP FOREIGN KEY fk_roster_change_visit;
ALTER TABLE incident DROP FOREIGN KEY fk_incident_visit;

-- report's tables -> core's and visit's
ALTER TABLE report DROP FOREIGN KEY fk_report_elder;
ALTER TABLE value_added_service_request
    DROP FOREIGN KEY fk_vas_request_elder,
    DROP FOREIGN KEY fk_vas_request_visit;
ALTER TABLE caregiver_review
    DROP FOREIGN KEY fk_review_family,
    DROP FOREIGN KEY fk_review_elder,
    DROP FOREIGN KEY fk_review_caregiver;

-- notification's tables -> core's
ALTER TABLE notification_subscription DROP FOREIGN KEY fk_subscription_elder;
