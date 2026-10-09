-- =====================================================================
-- Demonstration data: the family portal (FM01 - FM04)
-- =====================================================================
--
-- LOCAL ONLY. NOT A FLYWAY MIGRATION (no V prefix), for the reasons given at
-- the top of demo-seed.sql. Load it into the compose database after the
-- backend has started once (so Flyway has built the schema):
--
--   docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" carelink' \
--       < services/core/src/main/resources/db/demo/family-portal.sql
--
-- If the backend runs from ./mvnw against a MySQL installed on this machine
-- (application.yml's default localhost:3306), load it there instead:
--
--   mysql -h127.0.0.1 -ucarelink -pcarelink carelink < services/core/src/main/resources/db/demo/family-portal.sql
--
-- Every account below shares the demo password  Demo#2026  (bcrypt, as in
-- demo-seed.sql). Sign in from the landing page; FAMILY accounts land on /family.
--
--   demo-fam-wei      Lim Wei Ling   FULL access to two elders, primary contact.
--                                    The main account: elder switcher, a busy
--                                    schedule, a live visit, reports, intake history.
--                                    Her binding to her mother expires in 30 days.
--   demo-fam-weijie   Lim Wei Jie    READ_ONLY access to his father only.
--   demo-fam-huimin   Ong Hui Min    Binding is ACTIVE but expired yesterday:
--                                    signs in, sees "no elders".
--   demo-fam-kokwai   Chan Kok Wai   One binding PENDING_CONFIRMATION, one REVOKED:
--                                    signs in, sees "no elders".
--   demo-fam-aisyah   Nur Aisyah     Brand-new family: no bindings, one intake
--                                    application waiting for review.
--
-- Two caregiver accounts (demo-fam-cg-ming, demo-fam-cg-siti) exist only so the
-- visits have someone assigned; their credentials cover every status the
-- caregiver-details dialog distinguishes (no expiry, expiring, expired, revoked,
-- not yet valid, and a SUBMITTED one the family must not see).
--
-- DATES ARE RELATIVE TO THE DAY OF THE FIRST LOAD (Singapore date). Visits run
-- from 9 days before to 9 days after it, with statuses that match - closed in
-- the past, one IN_PROGRESS and one ARRIVED today, SCHEDULED afterwards. Weekly
-- reports cover the three complete Mon-Sun weeks before it.
--
-- SAFE TO RUN AGAIN. No explicit ids. Accounts, profiles and caregivers dedupe on
-- their unique keys; elders on name; visits and reports are inserted only when
-- the elder has none yet, so a second run never stacks a second schedule.
-- Bindings are re-asserted on every run, so a binding revoked while testing
-- comes back. To move the visits and reports to a new "today", run
-- family-portal-reset.sql first, then this file again.
-- =====================================================================

-- Hand-written DATETIMEs must be stored the way the application would have
-- stored them; see TIMESTAMPS in demo-seed.sql. A backend started with
-- ./mvnw spring-boot:run on a machine set to Singapore time needs 0. Use 8 when
-- the backend runs in the compose container (its JVM is on UTC) or on staging.
SET @appOffsetHours := 0;

-- CURRENT_DATE() follows the session zone, and the MySQL container runs on UTC.
SET time_zone = '+08:00';
SET @today   := CURRENT_DATE();
-- Monday of the last complete week before today.
SET @lastMon := @today - INTERVAL WEEKDAY(@today) DAY - INTERVAL 7 DAY;

START TRANSACTION;

-- ---------------------------------------------------------------- accounts ---
INSERT IGNORE INTO app_user (username, password_hash, display_name, enabled) VALUES
    ('demo-fam-wei',     '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Lim Wei Ling',  TRUE),
    ('demo-fam-weijie',  '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Lim Wei Jie',   TRUE),
    ('demo-fam-huimin',  '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Ong Hui Min',   TRUE),
    ('demo-fam-kokwai',  '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Chan Kok Wai',  TRUE),
    ('demo-fam-aisyah',  '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Nur Aisyah',    TRUE),
    ('demo-fam-cg-ming', '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Chen Ming',     TRUE),
    ('demo-fam-cg-siti', '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Siti Rahmah',   TRUE);

SET @uWei    := (SELECT id FROM app_user WHERE username = 'demo-fam-wei');
SET @uWeiJie := (SELECT id FROM app_user WHERE username = 'demo-fam-weijie');
SET @uHuiMin := (SELECT id FROM app_user WHERE username = 'demo-fam-huimin');
SET @uKokWai := (SELECT id FROM app_user WHERE username = 'demo-fam-kokwai');
SET @uAisyah := (SELECT id FROM app_user WHERE username = 'demo-fam-aisyah');
SET @uMing   := (SELECT id FROM app_user WHERE username = 'demo-fam-cg-ming');
SET @uSiti   := (SELECT id FROM app_user WHERE username = 'demo-fam-cg-siti');

INSERT IGNORE INTO user_role (user_id, role) VALUES
    (@uWei, 'FAMILY'), (@uWeiJie, 'FAMILY'), (@uHuiMin, 'FAMILY'), (@uKokWai, 'FAMILY'), (@uAisyah, 'FAMILY'),
    (@uMing, 'CAREGIVER'), (@uSiti, 'CAREGIVER');

-- -------------------------------------------------------- family profiles ---
INSERT IGNORE INTO family_member (user_id, full_name, phone, residential_address) VALUES
    (@uWei,    'Lim Wei Ling', '+6590010001', 'Blk 412 Ang Mo Kio Ave 10, #07-21'),
    (@uWeiJie, 'Lim Wei Jie',  '+6590010002', 'Blk 18 Bishan St 22, #03-110'),
    (@uHuiMin, 'Ong Hui Min',  '+6590010003', NULL),
    (@uKokWai, 'Chan Kok Wai', '+6590010004', NULL),
    (@uAisyah, 'Nur Aisyah',   NULL,          'Blk 765 Woodlands Ave 6, #09-14');

SET @fWei    := (SELECT id FROM family_member WHERE user_id = @uWei);
SET @fWeiJie := (SELECT id FROM family_member WHERE user_id = @uWeiJie);
SET @fHuiMin := (SELECT id FROM family_member WHERE user_id = @uHuiMin);
SET @fKokWai := (SELECT id FROM family_member WHERE user_id = @uKokWai);
SET @fAisyah := (SELECT id FROM family_member WHERE user_id = @uAisyah);

-- ------------------------------------------------------------------ elders ---
-- No accounts, so they dedupe on name.
INSERT INTO elder (full_name, gender, date_of_birth, phone, address, postal_code, sector,
                   preferred_dialects, lives_alone, mobility_level, continuity_preference, medical_notes)
SELECT 'Lim Boon Huat', 'MALE', '1941-06-18', '+6590020001', 'Blk 412 Ang Mo Kio Ave 10, #07-23', '560412', 'AMK',
       'Hokkien,Mandarin', FALSE, 'ASSISTIVE_CANE', 'PREFERRED',
       'Type 2 diabetes, mild hypertension. Uses a cane outdoors.'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM elder WHERE full_name = 'Lim Boon Huat');

INSERT INTO elder (full_name, gender, date_of_birth, phone, address, postal_code, sector,
                   preferred_dialects, lives_alone, mobility_level, continuity_preference, medical_notes)
SELECT 'Tan Siew Lan', 'FEMALE', '1945-02-03', '+6590020002', 'Blk 412 Ang Mo Kio Ave 10, #07-23', '560412', 'AMK',
       'Cantonese,Mandarin', FALSE, 'INDEPENDENT', 'NONE',
       'Early-stage dementia. Prefers morning visits.'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM elder WHERE full_name = 'Tan Siew Lan');

SET @eKow  := (SELECT MIN(id) FROM elder WHERE full_name = 'Lim Boon Huat');
SET @eSiew := (SELECT MIN(id) FROM elder WHERE full_name = 'Tan Siew Lan');

-- ---------------------------------------------------------------- bindings ---
-- Re-asserted on every run (status, scope, expiry), so testing a revocation in
-- the UI or by hand is undone by loading this file again.
INSERT INTO elder_family_binding (elder_id, family_member_id, relationship, is_primary_contact,
                                  access_scope, status, confirmed_at, expires_at)
VALUES
    (@eKow,  @fWei,    'DAUGHTER', TRUE,  'FULL',      'ACTIVE',
     TIMESTAMP(@today - INTERVAL 60 DAY, '10:00:00') + INTERVAL @appOffsetHours HOUR, NULL),
    (@eSiew, @fWei,    'DAUGHTER', FALSE, 'FULL',      'ACTIVE',
     TIMESTAMP(@today - INTERVAL 60 DAY, '10:00:00') + INTERVAL @appOffsetHours HOUR,
     TIMESTAMP(@today + INTERVAL 30 DAY, '23:59:00') + INTERVAL @appOffsetHours HOUR),
    (@eKow,  @fWeiJie, 'SON',      FALSE, 'READ_ONLY', 'ACTIVE',
     TIMESTAMP(@today - INTERVAL 40 DAY, '10:00:00') + INTERVAL @appOffsetHours HOUR, NULL),
    (@eSiew, @fHuiMin, 'OTHER',    FALSE, 'FULL',      'ACTIVE',
     TIMESTAMP(@today - INTERVAL 90 DAY, '10:00:00') + INTERVAL @appOffsetHours HOUR,
     TIMESTAMP(@today - INTERVAL 1 DAY, '18:00:00') + INTERVAL @appOffsetHours HOUR),
    (@eKow,  @fKokWai, 'OTHER',    FALSE, 'FULL',      'PENDING_CONFIRMATION', NULL, NULL),
    (@eSiew, @fKokWai, 'OTHER',    FALSE, 'READ_ONLY', 'REVOKED',
     TIMESTAMP(@today - INTERVAL 30 DAY, '10:00:00') + INTERVAL @appOffsetHours HOUR, NULL)
ON DUPLICATE KEY UPDATE
    relationship       = VALUES(relationship),
    is_primary_contact = VALUES(is_primary_contact),
    access_scope       = VALUES(access_scope),
    status             = VALUES(status),
    confirmed_at       = VALUES(confirmed_at),
    expires_at         = VALUES(expires_at);

-- ------------------------------------------------------ intake applications ---
-- What each applicant has asked for (FM01). Wei Ling's approved one is how her
-- father came on record; the other two show the review states.
INSERT INTO intake_application (applicant_family_member_id, target_elder_name, target_elder_age, target_address,
                                postal_code, mobility_level, preferred_dialects, care_needs, medical_notes,
                                status, review_remarks, created_at, reviewed_at, elder_id)
SELECT a.applicant, a.name, a.age, a.address, a.postal, a.mobility, a.dialects, a.needs, a.notes,
       a.status, a.remarks, a.created_at, a.reviewed_at, a.elder_id
  FROM (
        SELECT @fWei AS applicant, 'Lim Boon Huat' AS name, 85 AS age,
               'Blk 412 Ang Mo Kio Ave 10, #07-23' AS address, '560412' AS postal,
               'ASSISTIVE_CANE' AS mobility, 'Hokkien,Mandarin' AS dialects,
               CAST('["BATHING","VITALS","MEDICATION"]' AS JSON) AS needs,
               'Type 2 diabetes, mild hypertension.' AS notes, 'APPROVED' AS status,
               'Approved. Welcome to CareLink.' AS remarks,
               TIMESTAMP(@today - INTERVAL 70 DAY, '20:15:00') + INTERVAL @appOffsetHours HOUR AS created_at,
               TIMESTAMP(@today - INTERVAL 68 DAY, '11:00:00') + INTERVAL @appOffsetHours HOUR AS reviewed_at,
               @eKow AS elder_id
  UNION ALL SELECT @fWei, 'Lim Ah Mui', 79, 'Blk 9 Toa Payoh Lor 7, #02-15', '310009',
               'WHEELCHAIR_BEDBOUND', 'Teochew', CAST('["BATHING","FEEDING"]' AS JSON),
               'Recovering from hip surgery.', 'UNDER_REVIEW', NULL,
               TIMESTAMP(@today - INTERVAL 2 DAY, '21:40:00') + INTERVAL @appOffsetHours HOUR, NULL, NULL
  UNION ALL SELECT @fWei, 'Tan Ah Seng', 91, 'Blk 1 Sengkang Sq, #10-01', '540001',
               'INDEPENDENT', 'Cantonese', CAST('["COMPANIONSHIP"]' AS JSON),
               NULL, 'REJECTED', 'Outside our current service area. Please reapply when coverage expands.',
               TIMESTAMP(@today - INTERVAL 20 DAY, '09:05:00') + INTERVAL @appOffsetHours HOUR,
               TIMESTAMP(@today - INTERVAL 18 DAY, '15:30:00') + INTERVAL @appOffsetHours HOUR, NULL
  UNION ALL SELECT @fAisyah, 'Rahimah binte Osman', 82, 'Blk 765 Woodlands Ave 6, #09-16', '730765',
               'ASSISTIVE_CANE', 'Malay', CAST('["BATHING","MEDICATION","VITALS"]' AS JSON),
               'Arthritis in both knees.', 'SUBMITTED', NULL,
               TIMESTAMP(@today, '08:30:00') + INTERVAL @appOffsetHours HOUR, NULL, NULL
       ) AS a
 WHERE NOT EXISTS (SELECT 1 FROM intake_application i
                    WHERE i.applicant_family_member_id = a.applicant AND i.target_elder_name = a.name);

-- --------------------------------------------------------------- caregivers ---
INSERT IGNORE INTO caregiver (user_id, full_name, phone, sector, dialects, status) VALUES
    (@uMing, 'Chen Ming',   '+6590030001', 'AMK', 'Mandarin,Hokkien,Cantonese', 'AVAILABLE'),
    (@uSiti, 'Siti Rahmah', '+6590030002', 'AMK', 'Malay,English',              'AVAILABLE');

SET @cMing := (SELECT id FROM caregiver WHERE user_id = @uMing);
SET @cSiti := (SELECT id FROM caregiver WHERE user_id = @uSiti);

INSERT IGNORE INTO credential_type (name) VALUES
    ('First Aid'), ('Dementia Care'), ('Medication Administration'), ('Basic Caregiving'), ('Wound Care');

INSERT INTO credential (caregiver_id, credential_type_id, certificate_no, issuing_body, valid_from, expiry_date, status)
SELECT c.caregiver_id, (SELECT id FROM credential_type WHERE name = c.type_name),
       c.certificate_no, c.issuing_body, c.valid_from, c.expiry_date, c.status
  FROM (
        SELECT @cMing AS caregiver_id, 'First Aid' AS type_name, 'DEMOFAM-FA-0001' AS certificate_no,
               'Singapore Red Cross' AS issuing_body, @today - INTERVAL 1 YEAR AS valid_from,
               DATE '9999-12-31' AS expiry_date, 'PUBLISHED' AS status
  UNION ALL SELECT @cMing, 'Dementia Care', 'DEMOFAM-DC-0002', 'Dementia Singapore',
               @today - INTERVAL 2 YEAR, @today + INTERVAL 20 DAY, 'EXPIRING'
  UNION ALL SELECT @cMing, 'Medication Administration', 'DEMOFAM-MA-0003', 'AIC Learning Institute',
               @today - INTERVAL 3 YEAR, @today - INTERVAL 10 DAY, 'EXPIRED'
  UNION ALL SELECT @cMing, 'Wound Care', 'DEMOFAM-WC-0004', 'AIC Learning Institute',
               @today + INTERVAL 14 DAY, @today + INTERVAL 3 YEAR, 'PUBLISHED'
  UNION ALL SELECT @cMing, 'Basic Caregiving', 'DEMOFAM-BC-0005', 'AIC Learning Institute',
               @today - INTERVAL 5 DAY, @today + INTERVAL 2 YEAR, 'SUBMITTED'
  UNION ALL SELECT @cSiti, 'First Aid', 'DEMOFAM-FA-0006', 'Singapore Red Cross',
               @today - INTERVAL 6 MONTH, @today + INTERVAL 18 MONTH, 'PUBLISHED'
  UNION ALL SELECT @cSiti, 'Medication Administration', 'DEMOFAM-MA-0007', 'AIC Learning Institute',
               @today - INTERVAL 1 YEAR, @today + INTERVAL 1 YEAR, 'REVOKED'
       ) AS c
 WHERE NOT EXISTS (SELECT 1 FROM credential x
                    WHERE x.caregiver_id = c.caregiver_id AND x.certificate_no = c.certificate_no);

-- ------------------------------------------------------------------- visits ---
-- Planned in a session-only table first, so the follow-on rows (assignments,
-- transitions, tasks, vitals) can find each visit by a short key instead of
-- repeating its elder and start time.
CREATE TEMPORARY TABLE demo_fam_visit (
    k               VARCHAR(8)  NOT NULL PRIMARY KEY,
    elder_id        BIGINT      NOT NULL,
    caregiver_id    BIGINT      NULL,
    service_type    VARCHAR(50) NOT NULL,
    scheduled_start DATETIME    NOT NULL,
    scheduled_end   DATETIME    NULL,
    checked_in_at   DATETIME    NULL,
    checked_out_at  DATETIME    NULL,
    status          VARCHAR(20) NOT NULL,
    visit_id        BIGINT      NULL
);

-- (key, elder, caregiver, service, day offset from today, start, end, check-in, check-out, status)
INSERT INTO demo_fam_visit (k, elder_id, caregiver_id, service_type, scheduled_start, scheduled_end,
                            checked_in_at, checked_out_at, status)
SELECT p.k, p.elder_id, p.caregiver_id, p.service_type,
       TIMESTAMP(@today + INTERVAL p.d DAY, p.s) + INTERVAL @appOffsetHours HOUR,
       IF(p.e  IS NULL, NULL, TIMESTAMP(@today + INTERVAL p.d DAY, p.e)  + INTERVAL @appOffsetHours HOUR),
       IF(p.ci IS NULL, NULL, TIMESTAMP(@today + INTERVAL p.d DAY, p.ci) + INTERVAL @appOffsetHours HOUR),
       IF(p.co IS NULL, NULL, TIMESTAMP(@today + INTERVAL p.d DAY, p.co) + INTERVAL @appOffsetHours HOUR),
       p.status
  FROM (
        -- Lim Boon Huat: mostly Chen Ming, every status the family can see.
        SELECT 'k01' AS k, @eKow AS elder_id, @cMing AS caregiver_id, 'Personal care' AS service_type,
               -9 AS d, '09:00:00' AS s, '10:00:00' AS e, '09:03:00' AS ci, '09:57:00' AS co, 'VERIFIED' AS status
  UNION ALL SELECT 'k02', @eKow, @cSiti, 'Vitals check',          -7, '09:00:00', '09:45:00', '08:58:00', '09:40:00', 'VERIFIED'
  UNION ALL SELECT 'k03', @eKow, @cMing, 'Physiotherapy exercises', -5, '14:00:00', '15:00:00', '14:05:00', '15:01:00', 'AUTO_CLOSED'
  UNION ALL SELECT 'k04', @eKow, @cMing, 'Personal care',         -4, '09:00:00', '10:00:00', NULL,       NULL,       'EXCEPTION'
  UNION ALL SELECT 'k05', @eKow, @cSiti, 'Medication reminder',   -2, '09:00:00', '09:30:00', NULL,       NULL,       'CANCELLED'
  UNION ALL SELECT 'k06', @eKow, @cMing, 'Personal care',         -1, '09:00:00', '10:00:00', '09:01:00', '10:04:00', 'COMPLETED'
  UNION ALL SELECT 'k07', @eKow, @cMing, 'Personal care and vitals', 0, '09:00:00', '11:00:00', '09:02:00', NULL,     'IN_PROGRESS'
  UNION ALL SELECT 'k08', @eKow, @cSiti, 'Vitals check',           1, '09:00:00', '09:45:00', NULL,       NULL,       'SCHEDULED'
  UNION ALL SELECT 'k09', @eKow, NULL,   'Physiotherapy exercises', 2, '14:00:00', '15:00:00', NULL,       NULL,       'SCHEDULED'
  UNION ALL SELECT 'k10', @eKow, @cMing, 'Personal care',          3, '09:00:00', NULL,       NULL,       NULL,       'SCHEDULED'
  UNION ALL SELECT 'k11', @eKow, @cMing, 'Personal care',          6, '09:00:00', '10:00:00', NULL,       NULL,       'SCHEDULED'
  UNION ALL SELECT 'k12', @eKow, @cMing, 'Personal care',          9, '09:00:00', '10:00:00', NULL,       NULL,       'SCHEDULED'
        -- Tan Siew Lan: Siti Rahmah, mornings.
  UNION ALL SELECT 's01', @eSiew, @cSiti, 'Companionship and memory activities', -3, '08:30:00', '10:00:00', '08:31:00', '10:00:00', 'VERIFIED'
  UNION ALL SELECT 's02', @eSiew, @cSiti, 'Companionship and memory activities',  0, '08:30:00', '10:00:00', '08:34:00', NULL,       'ARRIVED'
  UNION ALL SELECT 's03', @eSiew, @cSiti, 'Meal preparation',                     2, '08:30:00', '09:30:00', NULL,       NULL,       'SCHEDULED'
       ) AS p;

-- Only on the first load: an elder that already has visits keeps its schedule.
INSERT INTO visit (elder_id, caregiver_id, service_type, scheduled_start, scheduled_end,
                   checked_in_at, checked_out_at, status)
SELECT d.elder_id, d.caregiver_id, d.service_type, d.scheduled_start, d.scheduled_end,
       d.checked_in_at, d.checked_out_at, d.status
  FROM demo_fam_visit d
 WHERE NOT EXISTS (SELECT 1 FROM visit v WHERE v.elder_id = d.elder_id);

-- Resolve each key to the row just inserted. On a re-run on a later day the
-- times no longer match, visit_id stays NULL and every insert below is skipped.
UPDATE demo_fam_visit d
  JOIN visit v ON v.elder_id = d.elder_id AND v.scheduled_start = d.scheduled_start
              AND v.service_type = d.service_type
   SET d.visit_id = v.id;

-- Assignment history. k08 was Chen Ming's until Siti Rahmah took it over.
INSERT INTO visit_assignment (visit_id, caregiver_id, status, reason, assigned_at, ended_at)
SELECT d.visit_id, d.caregiver_id, IF(d.status = 'CANCELLED', 'CANCELLED', 'ACTIVE'), 'Demo roster',
       d.scheduled_start - INTERVAL 7 DAY, IF(d.status = 'CANCELLED', d.scheduled_start - INTERVAL 1 DAY, NULL)
  FROM demo_fam_visit d
 WHERE d.visit_id IS NOT NULL AND d.caregiver_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM visit_assignment a WHERE a.visit_id = d.visit_id);

INSERT INTO visit_assignment (visit_id, caregiver_id, status, reason, assigned_at, ended_at)
SELECT d.visit_id, @cMing, 'REPLACED', 'Caregiver on leave; reassigned',
       d.scheduled_start - INTERVAL 10 DAY, d.scheduled_start - INTERVAL 2 DAY
  FROM demo_fam_visit d
 WHERE d.k = 'k08' AND d.visit_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM visit_assignment a WHERE a.visit_id = d.visit_id AND a.status = 'REPLACED');

-- State history (FM03 timeline). APPLIED rows are what the family sees; the one
-- REJECTED attempt on today's visit must stay hidden from them.
INSERT INTO visit_state_transition (visit_id, from_state, to_state, actor_user_id, result, rejection_reason, occurred_at)
SELECT d.visit_id, t.from_state, t.to_state,
       IF(t.actor_kind = 'cg', (SELECT c.user_id FROM caregiver c WHERE c.id = d.caregiver_id), NULL),
       t.result, t.reason,
       COALESCE(d.checked_in_at, d.scheduled_start) + INTERVAL t.after_min MINUTE
  FROM demo_fam_visit d
  JOIN (
        -- Closed and verified.
        SELECT 'VERIFIED' AS status, 'SCHEDULED' AS from_state, 'ARRIVED' AS to_state, 0 AS after_min,
               'APPLIED' AS result, NULL AS reason, 'cg' AS actor_kind
  UNION ALL SELECT 'VERIFIED',    'ARRIVED',     'IN_PROGRESS', 2,    'APPLIED', NULL, 'cg'
  UNION ALL SELECT 'VERIFIED',    'IN_PROGRESS', 'COMPLETED',   54,   'APPLIED', NULL, 'cg'
  UNION ALL SELECT 'VERIFIED',    'COMPLETED',   'VERIFIED',    240,  'APPLIED', NULL, 'sys'
        -- Elder never confirmed.
  UNION ALL SELECT 'AUTO_CLOSED', 'SCHEDULED',   'ARRIVED',     0,    'APPLIED', NULL, 'cg'
  UNION ALL SELECT 'AUTO_CLOSED', 'ARRIVED',     'IN_PROGRESS', 3,    'APPLIED', NULL, 'cg'
  UNION ALL SELECT 'AUTO_CLOSED', 'IN_PROGRESS', 'COMPLETED',   56,   'APPLIED', NULL, 'cg'
  UNION ALL SELECT 'AUTO_CLOSED', 'COMPLETED',   'AUTO_CLOSED', 1496, 'APPLIED', NULL, 'sys'
        -- Nobody checked in.
  UNION ALL SELECT 'EXCEPTION',   'SCHEDULED',   'EXCEPTION',   30,   'APPLIED', NULL, 'sys'
        -- Called off the day before.
  UNION ALL SELECT 'CANCELLED',   'SCHEDULED',   'CANCELLED',   -1200, 'APPLIED', NULL, 'sys'
        -- Checked out, waiting for the elder.
  UNION ALL SELECT 'COMPLETED',   'SCHEDULED',   'ARRIVED',     0,    'APPLIED', NULL, 'cg'
  UNION ALL SELECT 'COMPLETED',   'ARRIVED',     'IN_PROGRESS', 2,    'APPLIED', NULL, 'cg'
  UNION ALL SELECT 'COMPLETED',   'IN_PROGRESS', 'COMPLETED',   63,   'APPLIED', NULL, 'cg'
        -- Happening now.
  UNION ALL SELECT 'IN_PROGRESS', 'SCHEDULED',   'ARRIVED',     0,    'APPLIED', NULL, 'cg'
  UNION ALL SELECT 'IN_PROGRESS', 'ARRIVED',     'IN_PROGRESS', 4,    'APPLIED', NULL, 'cg'
  UNION ALL SELECT 'IN_PROGRESS', 'IN_PROGRESS', 'VERIFIED',    20,   'REJECTED',
               'VERIFIED is only reachable from COMPLETED', 'cg'
  UNION ALL SELECT 'ARRIVED',     'SCHEDULED',   'ARRIVED',     0,    'APPLIED', NULL, 'cg'
       ) AS t ON t.status = d.status
 WHERE d.visit_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM visit_state_transition x WHERE x.visit_id = d.visit_id)
   AND (t.actor_kind = 'sys' OR d.caregiver_id IS NOT NULL);

-- Tasks (FM03 checklist). Today's live visit has one of every task outcome.
INSERT INTO visit_task (visit_id, name, status, outcome, caregiver_note, completed_at)
SELECT d.visit_id, t.name, t.status, t.outcome, t.note,
       IF(t.done_min IS NULL, NULL, d.checked_in_at + INTERVAL t.done_min MINUTE)
  FROM demo_fam_visit d
  JOIN (
        SELECT 'k01' AS k, 'Assist with shower' AS name, 'DONE' AS status, NULL AS outcome,
               'Used the shower chair. Steady throughout.' AS note, 20 AS done_min
  UNION ALL SELECT 'k01', 'Foot check (diabetic)', 'DONE', 'No cuts or swelling', NULL, 35
  UNION ALL SELECT 'k02', 'Blood pressure',  'DONE', '138/86 mmHg', NULL, 10
  UNION ALL SELECT 'k02', 'Blood glucose',   'DONE', '7.2 mmol/L', 'Taken two hours after breakfast.', 15
  UNION ALL SELECT 'k03', 'Leg strengthening exercises', 'DONE', NULL, 'Completed two sets, a little breathless.', 40
  UNION ALL SELECT 'k06', 'Assist with shower', 'DONE', NULL, NULL, 25
  UNION ALL SELECT 'k06', 'Morning walk',       'DONE', NULL, 'Walked to the market and back with his cane.', 50
  UNION ALL SELECT 'k07', 'Assist with shower', 'DONE', NULL, 'In good spirits this morning.', 25
  UNION ALL SELECT 'k07', 'Blood pressure',     'DONE', '132/84 mmHg', NULL, 35
  UNION ALL SELECT 'k07', 'Morning walk',       'SKIPPED', NULL, 'Heavy rain; did seated exercises instead.', 45
  UNION ALL SELECT 'k07', 'Take morning medication', 'REFUSED', NULL, 'Said he would take it after lunch. Reminded him again.', 50
  UNION ALL SELECT 'k07', 'Prepare lunch',      'PENDING', NULL, NULL, NULL
  UNION ALL SELECT 'k08', 'Blood pressure',     'PENDING', NULL, NULL, NULL
  UNION ALL SELECT 'k08', 'Blood glucose',      'PENDING', NULL, NULL, NULL
  UNION ALL SELECT 's01', 'Memory card game',   'DONE', NULL, 'Remembered most of the family photos.', 30
  UNION ALL SELECT 's01', 'Tea and conversation', 'DONE', NULL, NULL, 75
  UNION ALL SELECT 's02', 'Memory card game',   'PENDING', NULL, NULL, NULL
  UNION ALL SELECT 's02', 'Tea and conversation', 'PENDING', NULL, NULL, NULL
       ) AS t ON t.k = d.k
 WHERE d.visit_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM visit_task x WHERE x.visit_id = d.visit_id);

INSERT INTO vital_sign (visit_id, metric, value, unit, out_of_range, recorded_at)
SELECT d.visit_id, r.metric, r.value, r.unit, r.out_of_range, d.checked_in_at + INTERVAL 10 MINUTE
  FROM demo_fam_visit d
  JOIN (
        SELECT 'k02' AS k, 'systolic' AS metric, 138.00 AS value, 'mmHg' AS unit, FALSE AS out_of_range
  UNION ALL SELECT 'k02', 'diastolic', 86.00, 'mmHg', FALSE
  UNION ALL SELECT 'k02', 'glucose',    7.20, 'mmol/L', FALSE
  UNION ALL SELECT 'k07', 'systolic', 132.00, 'mmHg', FALSE
  UNION ALL SELECT 'k07', 'diastolic', 84.00, 'mmHg', FALSE
       ) AS r ON r.k = d.k
 WHERE d.visit_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM vital_sign x WHERE x.visit_id = d.visit_id);

DROP TEMPORARY TABLE demo_fam_visit;

-- ------------------------------------------------------------------ reports ---
-- Weekly family reports for the three complete weeks before today, plus two
-- rows the family must never see (a DRAFT and an INTERNAL one), so a leak shows.
SET @disclaimer := 'This summary is prepared from care records for information only and does not constitute medical advice.';

INSERT INTO report (elder_id, audience, period_start, period_end, status, content, created_at)
SELECT r.elder_id, r.audience, r.period_start, r.period_start + INTERVAL 6 DAY, r.status,
       JSON_OBJECT('sections', r.sections, 'dataComplete', IF(r.complete, CAST('true' AS JSON), CAST('false' AS JSON)), 'missingItems', r.missing,
                   'disclaimer', IF(r.audience = 'FAMILY', @disclaimer, NULL), 'generatedBy', 'TEMPLATE'),
       TIMESTAMP(r.period_start + INTERVAL 7 DAY, '07:00:00') + INTERVAL @appOffsetHours HOUR
  FROM (
        SELECT @eKow AS elder_id, 'FAMILY' AS audience, @lastMon AS period_start, 'PUBLISHED' AS status,
               JSON_ARRAY(
                 JSON_OBJECT('title', 'Service completion', 'body',
                   'Four visits were planned. Two were completed, one was cancelled the day before, and one was missed because nobody arrived.\nThe missed visit has been raised with the care team.'),
                 JSON_OBJECT('title', 'Vital signs', 'body', 'Blood pressure 138/86 mmHg, within his usual range.\nBlood glucose 7.2 mmol/L after breakfast.'),
                 JSON_OBJECT('title', 'Observations', 'body', 'Managed his leg exercises but was a little breathless.\nWalked to the market with his cane.'),
                 JSON_OBJECT('title', 'Incidents', 'body', 'No incidents this week.')) AS sections,
               FALSE AS complete,
               JSON_ARRAY('One visit is waiting for Mr Lim to confirm it.') AS missing
  UNION ALL SELECT @eKow, 'FAMILY', @lastMon - INTERVAL 7 DAY, 'ARCHIVED',
               JSON_ARRAY(
                 JSON_OBJECT('title', 'Service completion', 'body', 'All three planned visits were completed.'),
                 JSON_OBJECT('title', 'Vital signs', 'body', 'Blood pressure readings were steady all week.'),
                 JSON_OBJECT('title', 'Observations', 'body', 'Cheerful and chatty. Asked about his grandchildren.'),
                 JSON_OBJECT('title', 'Incidents', 'body', 'No incidents this week.')),
               TRUE, JSON_ARRAY()
  UNION ALL SELECT @eKow, 'FAMILY', @lastMon - INTERVAL 14 DAY, 'PUBLISHED',
               JSON_ARRAY(
                 JSON_OBJECT('title', 'Service completion', 'body', 'Both planned visits were completed.'),
                 JSON_OBJECT('title', 'Vital signs', 'body', ''),
                 JSON_OBJECT('title', 'Observations', 'body', 'Settling in well with his new caregiver.'),
                 JSON_OBJECT('title', 'Incidents', 'body', 'No incidents this week.')),
               TRUE, JSON_ARRAY()
  UNION ALL SELECT @eKow, 'FAMILY', @lastMon, 'DRAFT',
               JSON_ARRAY(JSON_OBJECT('title', 'Service completion', 'body', 'DRAFT - must not be visible to family.')),
               FALSE, JSON_ARRAY()
  UNION ALL SELECT @eKow, 'INTERNAL', @lastMon, 'PUBLISHED',
               JSON_ARRAY(JSON_OBJECT('title', 'Service completion', 'body', 'INTERNAL - must not be visible to family.')),
               FALSE, JSON_ARRAY()
  UNION ALL SELECT @eSiew, 'FAMILY', @lastMon, 'PUBLISHED',
               JSON_ARRAY(
                 JSON_OBJECT('title', 'Service completion', 'body', 'The one planned visit was completed.'),
                 JSON_OBJECT('title', 'Vital signs', 'body', 'No readings were taken this week.'),
                 JSON_OBJECT('title', 'Observations', 'body', 'Remembered most of the family photos during the memory game.'),
                 JSON_OBJECT('title', 'Incidents', 'body', 'No incidents this week.')),
               TRUE, JSON_ARRAY()
       ) AS r
 WHERE NOT EXISTS (SELECT 1 FROM report x WHERE x.elder_id = r.elder_id);

-- A correction appended to last week's report: the original text stays as it was.
INSERT INTO report_amendment (report_id, note, author_user_id, created_at)
SELECT x.id, 'Correction: the missed visit was rescheduled to the following morning and took place as planned.',
       COALESCE((SELECT MIN(user_id) FROM user_role WHERE role = 'MANAGER'), @uMing),
       TIMESTAMP(@lastMon + INTERVAL 8 DAY, '15:20:00') + INTERVAL @appOffsetHours HOUR
  FROM report x
 WHERE x.elder_id = @eKow AND x.audience = 'FAMILY' AND x.status = 'PUBLISHED' AND x.period_start = @lastMon
   AND NOT EXISTS (SELECT 1 FROM report_amendment a WHERE a.report_id = x.id);

COMMIT;

SELECT u.username, fm.full_name, e.full_name AS elder, b.access_scope, b.status, b.expires_at
  FROM app_user u
  JOIN family_member fm ON fm.user_id = u.id
  LEFT JOIN elder_family_binding b ON b.family_member_id = fm.id
  LEFT JOIN elder e ON e.id = b.elder_id
 WHERE u.username LIKE 'demo-fam-%'
 ORDER BY u.username, e.full_name;
