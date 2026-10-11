-- The services an elder or a family may ask for on top of the care plan, as core's V14 and V22 laid them out in
-- the shared schema. A manager can change the catalogue afterwards; this runs once.
INSERT INTO value_added_service (name, description, duration_minutes, status) VALUES
    ('Hospital escort', 'Escort and assistance for a hospital or clinic appointment.', 180, 'AVAILABLE'),
    ('Grocery assistance', 'Help with grocery shopping and essential household purchases.', 90, 'AVAILABLE'),
    ('Companionship', 'Additional companionship and social support outside the regular care plan.', 120, 'AVAILABLE'),
    ('Light housekeeping', 'Light household assistance to support a safe living environment.', 120, 'AVAILABLE');
