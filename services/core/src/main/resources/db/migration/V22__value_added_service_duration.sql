-- How long one of each extra service takes (UC-EL02 / UC-FM08). Approving a request
-- dispatches a visit; without a length that visit had no end, so the replacement search
-- counted it as an hour against a caregiver's clashes and daily hours, however long a
-- hospital escort really runs. The visit now ends at start + duration_minutes.
ALTER TABLE value_added_service
    ADD COLUMN duration_minutes INT NOT NULL DEFAULT 60
        COMMENT 'length of the visit an approved request dispatches' AFTER description,
    ADD CONSTRAINT chk_vas_duration CHECK (duration_minutes > 0);

UPDATE value_added_service SET duration_minutes = 180 WHERE name = 'Hospital escort';
UPDATE value_added_service SET duration_minutes = 90  WHERE name = 'Grocery assistance';
UPDATE value_added_service SET duration_minutes = 120 WHERE name = 'Companionship';
UPDATE value_added_service SET duration_minutes = 120 WHERE name = 'Light housekeeping';
