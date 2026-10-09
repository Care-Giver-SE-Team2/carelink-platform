-- What the caregiver is told about a visit beyond its task. A care-plan visit's guidance
-- lives on its plan task; a visit no plan produced - an approved extra service - has only
-- the elder's own note ("bring the wheelchair"), copied here when the visit is dispatched so
-- the caregiver reads what was asked at the time, whatever happens to the request later.
ALTER TABLE visit
    ADD COLUMN instructions VARCHAR(1000) NULL
        COMMENT 'special instructions for the caregiver, e.g. from an extra-service request';
