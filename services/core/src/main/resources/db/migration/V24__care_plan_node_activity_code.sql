-- Which care activity a plan task delivers, as a code from the catalog families apply from
-- (careplan CareActivity: BATHING, VITALS, ...). The family's care needs are stored as the same
-- codes, so the manager's editor can tell which requests a plan covers without relying on the
-- task's name, which the manager is free to edit. NULL for a task named outside the catalog.
ALTER TABLE care_plan_node
    ADD COLUMN activity_code VARCHAR(50) NULL
        COMMENT 'care activity catalog code; null for a task outside the catalog' AFTER group_name;

-- Tasks published before this column existed were named after the catalog label they were
-- picked from, so the label identifies the activity.
UPDATE care_plan_node SET activity_code = 'BATHING'                     WHERE name = 'Bathing assistance';
UPDATE care_plan_node SET activity_code = 'GROOMING'                    WHERE name = 'Grooming';
UPDATE care_plan_node SET activity_code = 'MEAL_SUPPORT'                WHERE name = 'Meal support';
UPDATE care_plan_node SET activity_code = 'VITALS'                      WHERE name = 'Vital-sign check';
UPDATE care_plan_node SET activity_code = 'MORNING_MEDICATION_REMINDER' WHERE name = 'Morning reminder';
UPDATE care_plan_node SET activity_code = 'EVENING_MEDICATION_REMINDER' WHERE name = 'Evening reminder';
UPDATE care_plan_node SET activity_code = 'COMPANIONSHIP_WALK'          WHERE name = 'Companionship walk';
UPDATE care_plan_node SET activity_code = 'LIGHT_EXERCISE'              WHERE name = 'Light exercise';
UPDATE care_plan_node SET activity_code = 'ERRAND_ACCOMPANIMENT'        WHERE name = 'Errand accompaniment';
