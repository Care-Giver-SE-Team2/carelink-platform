INSERT INTO value_added_service (name, description, status)
SELECT 'Hospital escort', 'Escort and assistance for a hospital or clinic appointment.', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM value_added_service WHERE name = 'Hospital escort');

INSERT INTO value_added_service (name, description, status)
SELECT 'Grocery assistance', 'Help with grocery shopping and essential household purchases.', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM value_added_service WHERE name = 'Grocery assistance');

INSERT INTO value_added_service (name, description, status)
SELECT 'Companionship', 'Additional companionship and social support outside the regular care plan.', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM value_added_service WHERE name = 'Companionship');

INSERT INTO value_added_service (name, description, status)
SELECT 'Light housekeeping', 'Light household assistance to support a safe living environment.', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM value_added_service WHERE name = 'Light housekeeping');
