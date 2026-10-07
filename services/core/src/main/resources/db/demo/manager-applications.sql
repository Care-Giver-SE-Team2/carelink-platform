-- =====================================================================
-- Demonstration data: the manager's Applications tab (family intake review)
-- =====================================================================
--
-- NOT A FLYWAY MIGRATION (no V prefix), for the reasons given at the top of
-- demo-seed.sql. Load it the same way:
--
--   mysql -h127.0.0.1 -ucarelink -p carelink < services/core/src/main/resources/db/demo/manager-applications.sql
--
-- Five family applications waiting for an answer, submitted 3 hours to 4 days
-- ago so the response countdown shows every state, and a spread of checks:
--
--   Tan Bee Choo        (Grace Tan Wei Ling)  AMK  NEW       all checks pass
--   Mohd Yusof bin Ali  (Mohd Faizal)         TPY  NEW       Malay speaker free
--   Lim Soo Hwa         (Kevin Lim)           AMK  NEW       no Cantonese speaker in AMK
--   Ng Kim Lan          (Rachel Ng)           -    NO COVER  Yishun, no elder nearby; no mobile on file
--   Lakshmi Raman       (Priya Raman)         AMK  NEW       overdue
--
-- The screening learns an application's sector from elders already on record
-- in the same postal sector (first two postcode digits), so this adds two
-- elders to anchor AMK (56xxxx) and TPY (31xxxx), and one available caregiver
-- in each. Other demo scripts may add more caregivers in those sectors, which
-- only raises the counts.
--
-- One elder, one record: to show a family being refused, sign in as any
-- demo-app- applicant and apply for Lim Ah Huat at 560337 (already on record),
-- or as demo-app-grace and apply for Tan Bee Choo at 560230 again.
--
-- SAFE TO RUN AGAIN. Accounts, family members and caregivers dedupe on their
-- unique keys; elders and applications on name (plus postcode / applicant).
-- A rerun re-opens those of these five applications that were declined, so
-- declining can be demonstrated again. An approved one stays approved: its
-- elder and login are real records now, and approving it twice would be
-- refused as a second record for the same person.
--
-- The accounts share demo-seed.sql's password (Demo#2026) and its demo- prefix,
-- so each applicant can sign in to the family app and see their application.
-- =====================================================================

START TRANSACTION;

-- ---------------------------------------------------------------- accounts ---
INSERT IGNORE INTO app_user (username, password_hash, display_name, enabled) VALUES
    ('demo-app-grace',  '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Grace Tan Wei Ling', TRUE),
    ('demo-app-faizal', '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Mohd Faizal',        TRUE),
    ('demo-app-kevin',  '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Kevin Lim',          TRUE),
    ('demo-app-rachel', '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Rachel Ng',          TRUE),
    ('demo-app-priya',  '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Priya Raman',        TRUE),
    ('demo-app-cg-mei',   '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Chua Mei Hua',    TRUE),
    ('demo-app-cg-aisha', '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Aisha Binte Omar', TRUE);

SET @grace  := (SELECT id FROM app_user WHERE username = 'demo-app-grace');
SET @faizal := (SELECT id FROM app_user WHERE username = 'demo-app-faizal');
SET @kevin  := (SELECT id FROM app_user WHERE username = 'demo-app-kevin');
SET @rachel := (SELECT id FROM app_user WHERE username = 'demo-app-rachel');
SET @priya  := (SELECT id FROM app_user WHERE username = 'demo-app-priya');
SET @mei    := (SELECT id FROM app_user WHERE username = 'demo-app-cg-mei');
SET @aisha  := (SELECT id FROM app_user WHERE username = 'demo-app-cg-aisha');

INSERT IGNORE INTO user_role (user_id, role) VALUES
    (@grace, 'FAMILY'), (@faizal, 'FAMILY'), (@kevin, 'FAMILY'), (@rachel, 'FAMILY'), (@priya, 'FAMILY'),
    (@mei, 'CAREGIVER'), (@aisha, 'CAREGIVER');

-- ---------------------------------------------------------- family members ---
-- Rachel gave no mobile number, so her contact check fails.
INSERT IGNORE INTO family_member (user_id, full_name, phone) VALUES
    (@grace,  'Grace Tan Wei Ling', '+65 9123 4488'),
    (@faizal, 'Mohd Faizal',        '+65 8765 1203'),
    (@kevin,  'Kevin Lim',          '+65 9001 7714'),
    (@rachel, 'Rachel Ng',          NULL),
    (@priya,  'Priya Raman',        '+65 9654 3320');

SET @graceFm  := (SELECT id FROM family_member WHERE user_id = @grace);
SET @faizalFm := (SELECT id FROM family_member WHERE user_id = @faizal);
SET @kevinFm  := (SELECT id FROM family_member WHERE user_id = @kevin);
SET @rachelFm := (SELECT id FROM family_member WHERE user_id = @rachel);
SET @priyaFm  := (SELECT id FROM family_member WHERE user_id = @priya);

-- -------------------------------------------------------------- caregivers ---
INSERT IGNORE INTO caregiver (user_id, full_name, sector, dialects, status) VALUES
    (@mei,   'Chua Mei Hua',     'AMK', 'Hokkien,Mandarin', 'AVAILABLE'),
    (@aisha, 'Aisha Binte Omar', 'TPY', 'Malay,English',    'AVAILABLE');

-- ------------------------------------------------------------------ elders ---
-- No accounts, so dedupe on name and postcode.
INSERT INTO elder (full_name, gender, date_of_birth, address, postal_code, sector, preferred_dialects,
                   lives_alone, mobility_level, continuity_preference)
SELECT s.full_name, s.gender, s.dob, s.address, s.postal_code, s.sector, s.dialects, s.lives_alone,
       s.mobility, 'PREFERRED'
  FROM (
    SELECT 'Lim Ah Huat' AS full_name, 'MALE' AS gender, DATE '1945-06-02' AS dob,
           'Blk 337 Ang Mo Kio Ave 1, #07-1123' AS address, '560337' AS postal_code, 'AMK' AS sector,
           'Hokkien' AS dialects, TRUE AS lives_alone, 'ASSISTIVE_CANE' AS mobility
    UNION ALL SELECT 'Chew Siew Lan', 'FEMALE', DATE '1940-11-19', 'Blk 78 Toa Payoh Lor 4, #03-221', '310078',
           'TPY', 'Cantonese', FALSE, 'INDEPENDENT'
  ) s
 WHERE NOT EXISTS (SELECT 1 FROM elder e WHERE e.full_name = s.full_name AND e.postal_code = s.postal_code);

-- ------------------------------------------------------------ applications ---
-- created_at is stored in UTC, as the application writes it (UTC_TIMESTAMP()).
INSERT INTO intake_application (applicant_family_member_id, target_elder_name, target_elder_age, target_address,
                                postal_code, mobility_level, preferred_dialects, care_needs, medical_notes, status,
                                created_at)
SELECT s.fm, s.name, s.age, s.address, s.postal_code, s.mobility, s.dialects, CAST(s.needs AS JSON), s.notes,
       'SUBMITTED', UTC_TIMESTAMP() - INTERVAL s.hours_ago HOUR
  FROM (
    SELECT @graceFm AS fm, 'Tan Bee Choo' AS name, 83 AS age, 'Blk 230 Ang Mo Kio Ave 3, #04-117' AS address,
           '560230' AS postal_code, 'ASSISTIVE_CANE' AS mobility, 'Hokkien' AS dialects,
           '["BATHING", "Medication reminders", "Companionship"]' AS needs,
           'Hard of hearing on the left side. Some English. There is a dog.' AS notes, 3 AS hours_ago
    UNION ALL SELECT @faizalFm, 'Mohd Yusof bin Ali', 88, 'Blk 52 Toa Payoh Lor 6, #05-21', '310052',
           'WHEELCHAIR_BEDBOUND', 'Malay', '["BATHING", "VITALS"]',
           'Type 2 diabetes, insulin in the evening. Needs two people to transfer to the shower chair.', 19
    UNION ALL SELECT @kevinFm, 'Lim Soo Hwa', NULL, 'Blk 412 Ang Mo Kio Ave 10, #02-55', '560412',
           'ASSISTIVE_CANE', 'Cantonese', '["VITALS", "Meal preparation"]', NULL, 30
    UNION ALL SELECT @rachelFm, 'Ng Kim Lan', 91, 'Blk 708 Yishun Ave 5, #11-305', '760708',
           'INDEPENDENT', 'Teochew', '["Companionship", "Grocery runs"]',
           'Mild dementia, early stage. Forgets meals if nobody reminds her.', 50
    UNION ALL SELECT @priyaFm, 'Lakshmi Raman', 84, 'Blk 112 Ang Mo Kio Ave 4, #09-410', '560112',
           'ASSISTIVE_CANE', NULL, '["BATHING"]',
           'Recovering from a hip replacement in August. Physio twice a week at the polyclinic.', 98
  ) s
 WHERE s.fm IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM intake_application a
                    WHERE a.applicant_family_member_id = s.fm AND a.target_elder_name = s.name);

-- Re-open the declined ones with fresh submission times; leave approved ones be.
UPDATE intake_application
   SET status = 'SUBMITTED',
       reviewed_by_user_id = NULL,
       review_remarks = NULL,
       reviewed_at = NULL,
       elder_id = NULL,
       created_at = UTC_TIMESTAMP() - INTERVAL (CASE target_elder_name
                        WHEN 'Tan Bee Choo'       THEN 3
                        WHEN 'Mohd Yusof bin Ali' THEN 19
                        WHEN 'Lim Soo Hwa'        THEN 30
                        WHEN 'Ng Kim Lan'         THEN 50
                        ELSE 98 END) HOUR
 WHERE applicant_family_member_id IN (@graceFm, @faizalFm, @kevinFm, @rachelFm, @priyaFm)
   AND target_elder_name IN ('Tan Bee Choo', 'Mohd Yusof bin Ali', 'Lim Soo Hwa', 'Ng Kim Lan', 'Lakshmi Raman')
   AND status <> 'APPROVED';

COMMIT;
