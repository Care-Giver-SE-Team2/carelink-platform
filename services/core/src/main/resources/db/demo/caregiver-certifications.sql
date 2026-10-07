-- =====================================================================
-- Demonstration data: certificates for every caregiver (UC-MG06 register)
-- =====================================================================
--
-- NOT A FLYWAY MIGRATION (no V prefix), for the reasons given at the top of
-- demo-seed.sql. Load it after V11 has run, the same way:
--
--   mysql -h127.0.0.1 -ucarelink -p carelink < services/core/src/main/resources/db/demo/caregiver-certifications.sql
--
-- manager-certifications.sql stages one small, hand-written scenario on its own
-- six caregivers. This one fills the register instead: it gives a first aid
-- certificate and one other certificate to every caregiver already in the
-- database, whichever script or person created them, so the screen has enough
-- rows to page through and every state to look at.
--
-- Caregivers are numbered in id order and take turns through eight scenarios,
-- so with 24 caregivers each scenario appears three times:
--
--   0  manual handling, published, lapses in 400 days
--   1  dementia care lapses in 20 days, renewal submitted and waiting for review
--   2  medication prompt lapses in 9 days, caregiver reminded and yet to submit
--   3  manual handling lapses in 24 days, caregiver reminded and yet to submit
--   4  dementia care, published, lapses in 200 days
--   5  medication prompt lapses in 16 days, renewal rejected as unreadable
--   6  dementia care lapses in 27 days, renewal rejected as unverifiable
--   7  first aid lapses in 12 days, renewal submitted and waiting for review
--
-- Every row has a date in the Expires column: nothing is permanent, and every
-- submission is a renewal, so it counts down to the certificate it replaces. A
-- rejected renewal brings that certificate back as a row of its own, so the
-- caregivers in scenarios 5 and 6 show two rows of the same type. Every first aid
-- certificate outside scenario 7 lapses two months to two years out, clear of
-- the 30-day reminder window. Dates are relative to today, so the register
-- shows the same picture whenever it is loaded.
--
-- Certificate numbers all start DEMOC- and are derived from the caregiver id.
-- A caregiver who holds any certificate without that prefix - one from another
-- demo script, or one submitted through the app - is left alone entirely.
--
-- SAFE TO RUN AGAIN. A rerun deletes this script's DEMOC- certificates for the
-- caregivers it covers and inserts them afresh, so a review can be demonstrated
-- again after publishing or rejecting.
-- =====================================================================

SET time_zone = '+08:00';
START TRANSACTION;
SET @today   := CURRENT_DATE();
SET @manager := (SELECT MIN(user_id) FROM user_role WHERE role = 'MANAGER');

-- name is unique, so a type another script already added is reused.
INSERT IGNORE INTO credential_type (name) VALUES
    ('First aid'), ('Manual handling'), ('Dementia care'), ('Medication prompt');

SET @firstAid   := (SELECT id FROM credential_type WHERE name = 'First aid');
SET @handling   := (SELECT id FROM credential_type WHERE name = 'Manual handling');
SET @dementia   := (SELECT id FROM credential_type WHERE name = 'Dementia care');
SET @medication := (SELECT id FROM credential_type WHERE name = 'Medication prompt');

-- The caregivers this script covers, and the scenario each one gets.
DROP TEMPORARY TABLE IF EXISTS demo_cert_roster;
CREATE TEMPORARY TABLE demo_cert_roster (
    caregiver_id BIGINT NOT NULL PRIMARY KEY,
    scenario     INT    NOT NULL
);
INSERT INTO demo_cert_roster (caregiver_id, scenario)
SELECT c.id, (ROW_NUMBER() OVER (ORDER BY c.id) - 1) % 8
  FROM caregiver c
 WHERE NOT EXISTS (SELECT 1 FROM credential x
                    WHERE x.caregiver_id = c.id
                      AND (x.certificate_no IS NULL OR x.certificate_no NOT LIKE 'DEMOC-%'));

-- Clear an earlier run. Renewal links go first so the self-reference does not
-- block the delete.
UPDATE credential c
  JOIN demo_cert_roster r ON r.caregiver_id = c.caregiver_id
   SET c.renews_credential_id = NULL
 WHERE c.certificate_no LIKE 'DEMOC-%';

DELETE c FROM credential c
  JOIN demo_cert_roster r ON r.caregiver_id = c.caregiver_id
 WHERE c.certificate_no LIKE 'DEMOC-%';

-- First aid for everyone.
INSERT INTO credential (caregiver_id, credential_type_id, certificate_no, issuing_body,
                        expiry_date, status, created_at)
SELECT r.caregiver_id, @firstAid, CONCAT('DEMOC-SRC-FA-', 40000 + r.caregiver_id), 'Singapore Red Cross',
       DATE_ADD(@today, INTERVAL CASE WHEN r.scenario = 7 THEN 12
                                      ELSE 60 + (r.caregiver_id * 37) % 600 END DAY),
       'PUBLISHED', TIMESTAMP(DATE_SUB(@today, INTERVAL 1 YEAR), '10:00:00')
  FROM demo_cert_roster r;

-- One more certificate each, by scenario.
INSERT INTO credential (caregiver_id, credential_type_id, certificate_no, issuing_body,
                        expiry_date, status, created_at)
SELECT r.caregiver_id,
       CASE WHEN r.scenario IN (1, 4, 6) THEN @dementia
            WHEN r.scenario IN (2, 5)    THEN @medication
            ELSE @handling END,
       CONCAT(CASE WHEN r.scenario IN (1, 4, 6) THEN 'DEMOC-DSG-'
                   WHEN r.scenario IN (2, 5)    THEN 'DEMOC-MP-'
                   ELSE 'DEMOC-MH-' END, 50000 + r.caregiver_id),
       CASE WHEN r.scenario IN (1, 4, 6) THEN 'Dementia Singapore'
            ELSE 'Agency for Integrated Care' END,
       DATE_ADD(@today, INTERVAL CASE r.scenario
                                     WHEN 0 THEN 400
                                     WHEN 1 THEN 20
                                     WHEN 2 THEN 9
                                     WHEN 3 THEN 24
                                     WHEN 4 THEN 200
                                     WHEN 5 THEN 16
                                     WHEN 6 THEN 27
                                     WHEN 7 THEN 500 END DAY),
       'PUBLISHED', TIMESTAMP(DATE_SUB(@today, INTERVAL 8 MONTH), '10:00:00')
  FROM demo_cert_roster r;

-- The renewals: submitted (1, 7) or rejected (5, 6). Each
-- renews the caregiver's certificate of the same type and runs two years past it.
-- Only one certificate of each type exists per caregiver at this point, so the
-- type alone finds the one being renewed.
INSERT INTO credential (caregiver_id, credential_type_id, certificate_no, issuing_body,
                        expiry_date, status, renews_credential_id,
                        reviewed_by_user_id, review_note, reviewed_at, created_at)
SELECT r.caregiver_id, o.credential_type_id,
       CONCAT(LEFT(o.certificate_no, CHAR_LENGTH(o.certificate_no) - 5), 70000 + r.caregiver_id),
       o.issuing_body,
       DATE_ADD(o.expiry_date, INTERVAL 2 YEAR),
       CASE WHEN r.scenario IN (5, 6) THEN 'REJECTED' ELSE 'SUBMITTED' END,
       o.id,
       CASE WHEN r.scenario IN (5, 6) THEN @manager END,
       CASE r.scenario
            WHEN 5 THEN 'The expiry date is cut off at the bottom of the scan - please submit a photo of the whole certificate.'
            WHEN 6 THEN 'The certificate number does not appear in Dementia Singapore''s register of course graduates.' END,
       CASE WHEN r.scenario IN (5, 6) THEN TIMESTAMP(DATE_SUB(@today, INTERVAL 2 DAY), '15:20:00') END,
       TIMESTAMP(DATE_SUB(@today, INTERVAL CASE WHEN r.scenario IN (5, 6) THEN 4 ELSE 1 END DAY),
                 MAKETIME(18 + r.caregiver_id % 4, r.caregiver_id * 7 % 60, 0))
  FROM demo_cert_roster r
  JOIN credential o ON o.caregiver_id = r.caregiver_id
                   AND o.certificate_no LIKE 'DEMOC-%'
                   AND o.credential_type_id = CASE r.scenario
                                                   WHEN 1 THEN @dementia
                                                   WHEN 5 THEN @medication
                                                   WHEN 6 THEN @dementia
                                                   WHEN 7 THEN @firstAid END
 WHERE r.scenario IN (1, 5, 6, 7);

DROP TEMPORARY TABLE demo_cert_roster;

COMMIT;
