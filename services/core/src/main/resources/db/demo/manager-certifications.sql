-- =====================================================================
-- Demonstration data: the manager's Certifications screen (UC-MG06)
-- =====================================================================
--
-- NOT A FLYWAY MIGRATION (no V prefix), for the reasons given at the top of
-- demo-seed.sql. Load it after V11 has run, the same way:
--
--   mysql -h127.0.0.1 -ucarelink -p carelink < services/core/src/main/resources/db/demo/manager-certifications.sql
--
-- Six caregivers and their certificates, dated relative to today so the
-- register always shows the same picture: two submissions waiting for review
-- (a first aid renewal 12 days before the old one lapses, and a new dementia
-- care certificate), two certificates inside the 30-day reminder window, and
-- two well clear of it.
--
-- SAFE TO RUN AGAIN. Accounts and caregivers dedupe on their unique keys and
-- certificates on (caregiver, certificate number). A rerun also resets these
-- certificates - and only these - to the scenario above, so the review can be
-- demonstrated again after publishing or rejecting.
--
-- The accounts share demo-seed.sql's password (Demo#2026) and its demo- prefix.
-- =====================================================================

SET time_zone = '+08:00';
START TRANSACTION;
SET @today := CURRENT_DATE();

INSERT IGNORE INTO app_user (username, password_hash, display_name, enabled) VALUES
    ('demo-cert-devi',   '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Devi Raman',       TRUE),
    ('demo-cert-rosnah', '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Rosnah Binte Ali', TRUE),
    ('demo-cert-nur',    '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Nur Aisyah',       TRUE),
    ('demo-cert-ong',    '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Ong Wei Jie',      TRUE),
    ('demo-cert-siti',   '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Siti Rahmah',      TRUE),
    ('demo-cert-kamala', '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Kamala Devi',      TRUE);

SET @devi   := (SELECT id FROM app_user WHERE username = 'demo-cert-devi');
SET @rosnah := (SELECT id FROM app_user WHERE username = 'demo-cert-rosnah');
SET @nur    := (SELECT id FROM app_user WHERE username = 'demo-cert-nur');
SET @ong    := (SELECT id FROM app_user WHERE username = 'demo-cert-ong');
SET @siti   := (SELECT id FROM app_user WHERE username = 'demo-cert-siti');
SET @kamala := (SELECT id FROM app_user WHERE username = 'demo-cert-kamala');

INSERT IGNORE INTO user_role (user_id, role) VALUES
    (@devi, 'CAREGIVER'), (@rosnah, 'CAREGIVER'), (@nur, 'CAREGIVER'),
    (@ong, 'CAREGIVER'), (@siti, 'CAREGIVER'), (@kamala, 'CAREGIVER');

INSERT IGNORE INTO caregiver (user_id, full_name, sector, dialects, status) VALUES
    (@devi,   'Devi Raman',       'AMK', 'Tamil,English',    'AVAILABLE'),
    (@rosnah, 'Rosnah Binte Ali', 'TPY', 'Malay,English',    'AVAILABLE'),
    (@nur,    'Nur Aisyah',       'AMK', 'Malay,English',    'AVAILABLE'),
    (@ong,    'Ong Wei Jie',      'TPY', 'Hokkien,Mandarin', 'AVAILABLE'),
    (@siti,   'Siti Rahmah',      'AMK', 'Malay',            'AVAILABLE'),
    (@kamala, 'Kamala Devi',      'TPY', 'Tamil',            'AVAILABLE');

SET @deviCg   := (SELECT id FROM caregiver WHERE user_id = @devi);
SET @rosnahCg := (SELECT id FROM caregiver WHERE user_id = @rosnah);
SET @nurCg    := (SELECT id FROM caregiver WHERE user_id = @nur);
SET @ongCg    := (SELECT id FROM caregiver WHERE user_id = @ong);
SET @sitiCg   := (SELECT id FROM caregiver WHERE user_id = @siti);
SET @kamalaCg := (SELECT id FROM caregiver WHERE user_id = @kamala);

-- name is unique, so a type another script already added is reused.
INSERT IGNORE INTO credential_type (name) VALUES
    ('First aid'), ('Manual handling'), ('Dementia care'), ('Medication prompt');

SET @firstAid   := (SELECT id FROM credential_type WHERE name = 'First aid');
SET @handling   := (SELECT id FROM credential_type WHERE name = 'Manual handling');
SET @dementia   := (SELECT id FROM credential_type WHERE name = 'Dementia care');
SET @medication := (SELECT id FROM credential_type WHERE name = 'Medication prompt');

INSERT INTO credential (caregiver_id, credential_type_id, certificate_no, issuing_body, expiry_date, status)
SELECT s.caregiver_id, s.type_id, s.cert, s.issuer, '9999-12-31', 'PUBLISHED'
  FROM (
    SELECT @deviCg   AS caregiver_id, @firstAid   AS type_id, 'DEMO-SRC-FA-51007' AS cert, 'Singapore Red Cross' AS issuer
    UNION ALL SELECT @deviCg,   @firstAid,   'DEMO-SRC-FA-88412', 'Singapore Red Cross'
    UNION ALL SELECT @rosnahCg, @dementia,   'DEMO-DSG-20931',    'Dementia Singapore'
    UNION ALL SELECT @nurCg,    @handling,   'DEMO-MH-30114',     'Agency for Integrated Care'
    UNION ALL SELECT @ongCg,    @dementia,   'DEMO-DSG-17702',    'Dementia Singapore'
    UNION ALL SELECT @sitiCg,   @firstAid,   'DEMO-SRC-FA-40266', 'Singapore Red Cross'
    UNION ALL SELECT @kamalaCg, @medication, 'DEMO-MP-11950',     'Agency for Integrated Care'
  ) s
 WHERE s.caregiver_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM credential c WHERE c.caregiver_id = s.caregiver_id AND c.certificate_no = s.cert);

-- Reset to the scenario, whatever an earlier demonstration did to them.
UPDATE credential
   SET status = CASE certificate_no
                  WHEN 'DEMO-SRC-FA-88412' THEN 'SUBMITTED'
                  WHEN 'DEMO-DSG-20931'    THEN 'SUBMITTED'
                  ELSE 'PUBLISHED' END,
       expiry_date = CASE certificate_no
                  WHEN 'DEMO-SRC-FA-51007' THEN DATE_ADD(@today, INTERVAL 12 DAY)
                  WHEN 'DEMO-SRC-FA-88412' THEN DATE_ADD(@today, INTERVAL 23 MONTH)
                  WHEN 'DEMO-DSG-20931'    THEN DATE_ADD(@today, INTERVAL 3 YEAR)
                  WHEN 'DEMO-MH-30114'     THEN DATE_ADD(@today, INTERVAL 21 DAY)
                  WHEN 'DEMO-DSG-17702'    THEN DATE_ADD(@today, INTERVAL 29 DAY)
                  WHEN 'DEMO-SRC-FA-40266' THEN DATE_ADD(@today, INTERVAL 245 DAY)
                  WHEN 'DEMO-MP-11950'     THEN DATE_ADD(@today, INTERVAL 335 DAY) END,
       valid_from = NULL,
       renews_credential_id = NULL,
       reviewed_by_user_id = NULL,
       review_note = NULL,
       reviewed_at = NULL,
       created_at = CASE WHEN certificate_no IN ('DEMO-SRC-FA-88412', 'DEMO-DSG-20931')
                         THEN TIMESTAMP(DATE_SUB(@today, INTERVAL 1 DAY), '21:04:00') ELSE created_at END
 WHERE caregiver_id IN (@deviCg, @rosnahCg, @nurCg, @ongCg, @sitiCg, @kamalaCg)
   AND certificate_no LIKE 'DEMO-%';

-- Devi's submission renews her current first aid certificate.
SET @deviOld := (SELECT id FROM credential WHERE caregiver_id = @deviCg AND certificate_no = 'DEMO-SRC-FA-51007');
UPDATE credential SET renews_credential_id = @deviOld
 WHERE caregiver_id = @deviCg AND certificate_no = 'DEMO-SRC-FA-88412';

COMMIT;
