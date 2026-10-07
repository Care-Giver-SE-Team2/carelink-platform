-- =====================================================================
-- Demonstration data: the manager's Absences and Quality screens
-- (UC-MG04 re-roster on caregiver absence, UC-MG08 spot checks)
-- =====================================================================
--
-- NOT A FLYWAY MIGRATION (no V prefix), for the reasons given at the top of
-- demo-seed.sql. Load it after V13 has run and after demo-seed.sql, whose
-- elders (Grace Wee, Chua Ah Moi), family member (Fiona Rahman) and managers
-- it uses, the same way:
--
--   ssh -i ~/.ssh/care-link.pem ubuntu@HOST 'sudo /opt/carelink/load-demo-data.sh' \
--       < services/core/src/main/resources/db/demo/manager-absences-and-spot-checks.sql
--
-- WHAT IT LAYS OUT, dated from the day it is loaded (D):
--
--   Absences. Aisha Rahim is on two days of medical leave from D+2, already
--   approved. Her three visits on those days - two with Grace Wee, one with
--   Chua Ah Moi - are waiting to be re-rostered, and the other caregivers give
--   the search one reason of each kind to show:
--
--     Tan Mei Ling   has looked after Grace five times     ranked first
--     Farah Aziz     her last spot check met the standard
--     Lee Hui Min    her last spot check needed improvement
--     Goh Siew Lan   on approved leave the same two days   excluded
--     Joseph Lim     with Chua when Grace's first visit is due   excluded
--     Ravi Kumar     still onboarding, nothing published  excluded
--
--   Nora Ismail has asked for a day off on D+6 and is waiting for a decision,
--   the way a caregiver's own request arrives; approving it vacates her visit
--   with Grace that day.
--
--   Spot checks, on Grace's visits: one waiting for Fiona's consent (Mei Ling,
--   D+4) and one she agreed to (Farah, D+5), for the manager to record. Two
--   concluded a fortnight before D, at Chua's visits - Farah met the standard,
--   Hui Min needed improvement - which the re-rostering search reads. Chua's
--   daughter Lily gave the consent for those, so she is added, bound to her
--   mother.
--
-- SAFE TO RUN AGAIN. Accounts, caregivers, certificates and the binding dedupe
-- on their unique keys, the history on whether it is there already. The dated
-- part is laid out again only once the last one has been used (re-rostered)
-- or its days have passed, and then always after it, so a rehearsal can be
-- followed by a second load for the real demonstration. Otherwise a second run
-- changes nothing. Nothing here updates or deletes a row.
--
-- Every account shares demo-seed.sql's password (Demo#2026) and demo- prefix.
-- =====================================================================

-- TIMESTAMPS are written the way the application stores them: the Singapore
-- wall-clock plus @appOffsetHours, for the reason given in demo-seed.sql.
-- Dates (leave days, certificate expiry) are stored as they are.
SET time_zone = '+08:00';
SET @appOffsetHours := 8;

START TRANSACTION;

SET @today := CURRENT_DATE();
SET @nowStored := NOW() + INTERVAL @appOffsetHours HOUR;

-- ---------------------------------------------------------------- accounts ---
INSERT IGNORE INTO app_user (username, password_hash, display_name, enabled) VALUES
    ('demo-aisha',   '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Aisha Rahim',  TRUE),
    ('demo-meiling', '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Tan Mei Ling', TRUE),
    ('demo-farah',   '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Farah Aziz',   TRUE),
    ('demo-huimin',  '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Lee Hui Min',  TRUE),
    ('demo-siewlan', '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Goh Siew Lan', TRUE),
    ('demo-joseph',  '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Joseph Lim',   TRUE),
    ('demo-ravi',    '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Ravi Kumar',   TRUE),
    ('demo-nora',    '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Nora Ismail',  TRUE),
    ('demo-lily',    '{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm', 'Lily Chua',    TRUE);

SET @alice   := (SELECT id FROM app_user WHERE username = 'demo-alice');
SET @fiona   := (SELECT id FROM app_user WHERE username = 'demo-fiona');
SET @aisha   := (SELECT id FROM app_user WHERE username = 'demo-aisha');
SET @meiling := (SELECT id FROM app_user WHERE username = 'demo-meiling');
SET @farah   := (SELECT id FROM app_user WHERE username = 'demo-farah');
SET @huimin  := (SELECT id FROM app_user WHERE username = 'demo-huimin');
SET @siewlan := (SELECT id FROM app_user WHERE username = 'demo-siewlan');
SET @joseph  := (SELECT id FROM app_user WHERE username = 'demo-joseph');
SET @ravi    := (SELECT id FROM app_user WHERE username = 'demo-ravi');
SET @nora    := (SELECT id FROM app_user WHERE username = 'demo-nora');
SET @lily    := (SELECT id FROM app_user WHERE username = 'demo-lily');

INSERT IGNORE INTO user_role (user_id, role) VALUES
    (@aisha, 'CAREGIVER'), (@meiling, 'CAREGIVER'), (@farah, 'CAREGIVER'), (@huimin, 'CAREGIVER'),
    (@siewlan, 'CAREGIVER'), (@joseph, 'CAREGIVER'), (@ravi, 'CAREGIVER'), (@nora, 'CAREGIVER'),
    (@lily, 'FAMILY');

-- -------------------------------------------------------------- caregivers ---
-- Grace is in AMK and speaks Hokkien and Mandarin; Chua is in TPY and speaks
-- Teochew. Sector and dialect are the search's soft rules, so they are chosen
-- to give each person a different line on the screen.
INSERT IGNORE INTO caregiver (user_id, full_name, phone, sector, dialects, status) VALUES
    (@aisha,   'Aisha Rahim',  '+6591110021', 'AMK', 'Malay,English',      'AVAILABLE'),
    (@meiling, 'Tan Mei Ling', '+6591110022', 'AMK', 'Hokkien,Mandarin',   'AVAILABLE'),
    (@farah,   'Farah Aziz',   '+6591110023', 'AMK', 'Malay,English',      'AVAILABLE'),
    (@huimin,  'Lee Hui Min',  '+6591110024', 'TPY', 'Teochew,Mandarin',   'AVAILABLE'),
    (@siewlan, 'Goh Siew Lan', '+6591110025', 'AMK', 'Cantonese,Mandarin', 'AVAILABLE'),
    (@joseph,  'Joseph Lim',   '+6591110026', 'AMK', 'Hokkien,English',    'AVAILABLE'),
    (@ravi,    'Ravi Kumar',   '+6591110027', 'AMK', 'Tamil,English',      'ONBOARDING'),
    (@nora,    'Nora Ismail',  '+6591110028', 'TPY', 'Malay',              'AVAILABLE');

SET @aishaCg   := (SELECT id FROM caregiver WHERE user_id = @aisha);
SET @meilingCg := (SELECT id FROM caregiver WHERE user_id = @meiling);
SET @farahCg   := (SELECT id FROM caregiver WHERE user_id = @farah);
SET @huiminCg  := (SELECT id FROM caregiver WHERE user_id = @huimin);
SET @siewlanCg := (SELECT id FROM caregiver WHERE user_id = @siewlan);
SET @josephCg  := (SELECT id FROM caregiver WHERE user_id = @joseph);
SET @noraCg    := (SELECT id FROM caregiver WHERE user_id = @nora);

-- An available caregiver has a published certificate (the caregiver table's
-- status comment). A first aid one that does not expire keeps these people out
-- of the expiry reminders the certifications demonstration is about. Ravi has
-- none: he is still onboarding.
INSERT IGNORE INTO credential_type (name) VALUES ('First aid');
SET @firstAid := (SELECT id FROM credential_type WHERE name = 'First aid');

INSERT INTO credential (caregiver_id, credential_type_id, certificate_no, issuing_body, expiry_date, status)
SELECT s.caregiver_id, @firstAid, s.cert, 'Singapore Red Cross', '9999-12-31', 'PUBLISHED'
  FROM (
        SELECT @aishaCg AS caregiver_id, 'DEMO-SRC-FA-62101' AS cert
  UNION ALL SELECT @meilingCg, 'DEMO-SRC-FA-62102'
  UNION ALL SELECT @farahCg,   'DEMO-SRC-FA-62103'
  UNION ALL SELECT @huiminCg,  'DEMO-SRC-FA-62104'
  UNION ALL SELECT @siewlanCg, 'DEMO-SRC-FA-62105'
  UNION ALL SELECT @josephCg,  'DEMO-SRC-FA-62106'
  UNION ALL SELECT @noraCg,    'DEMO-SRC-FA-62108'
       ) AS s
 WHERE s.caregiver_id IS NOT NULL
   AND @firstAid IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM credential c WHERE c.caregiver_id = s.caregiver_id AND c.certificate_no = s.cert);

-- ------------------------------------------------------------------ family ---
SET @graceElder := (SELECT e.id FROM elder e JOIN app_user u ON u.id = e.user_id WHERE u.username = 'demo-grace');
SET @chuaElder  := (SELECT id FROM elder WHERE full_name = 'Chua Ah Moi' AND user_id IS NULL ORDER BY id LIMIT 1);
SET @fionaFamily := (SELECT id FROM family_member WHERE user_id = @fiona);

-- Chua's medical notes name her daughter as the primary contact.
INSERT IGNORE INTO family_member (user_id, full_name, phone, residential_address) VALUES
    (@lily, 'Lily Chua', '+6591110029', 'Blk 88 Toa Payoh Lor 4, #11-09');
SET @lilyFamily := (SELECT id FROM family_member WHERE user_id = @lily);

INSERT IGNORE INTO elder_family_binding (elder_id, family_member_id, relationship,
                                         is_primary_contact, access_scope, status, confirmed_at)
SELECT @chuaElder, @lilyFamily, 'DAUGHTER', TRUE, 'FULL', 'ACTIVE',
       TIMESTAMP(@today - INTERVAL 60 DAY, '10:00:00') + INTERVAL @appOffsetHours HOUR
  FROM DUAL
 WHERE @chuaElder IS NOT NULL AND @lilyFamily IS NOT NULL;

-- ----------------------------------------------------------------- history ---
-- Five afternoons Mei Ling spent with Grace over the past five weeks: what the
-- continuity rule counts. Added once; they stay within the 180 days it looks
-- back for months afterwards.
SET @meilingHistory := (SELECT CASE
        WHEN @meilingCg IS NULL OR @graceElder IS NULL THEN 0
        WHEN EXISTS (SELECT 1 FROM visit WHERE elder_id = @graceElder AND caregiver_id = @meilingCg
                                         AND status = 'VERIFIED') THEN 0
        ELSE 1 END);

INSERT INTO visit (elder_id, caregiver_id, service_type, scheduled_start, scheduled_end,
                   checked_in_at, checked_out_at, status)
SELECT @graceElder, @meilingCg, 'Personal care', h.starts, h.starts + INTERVAL 1 HOUR,
       h.starts + INTERVAL 2 MINUTE, h.starts + INTERVAL 58 MINUTE, 'VERIFIED'
  FROM (
        SELECT TIMESTAMP(@today - INTERVAL 35 DAY, '15:00:00') + INTERVAL @appOffsetHours HOUR AS starts
  UNION ALL SELECT TIMESTAMP(@today - INTERVAL 28 DAY, '15:00:00') + INTERVAL @appOffsetHours HOUR
  UNION ALL SELECT TIMESTAMP(@today - INTERVAL 21 DAY, '15:00:00') + INTERVAL @appOffsetHours HOUR
  UNION ALL SELECT TIMESTAMP(@today - INTERVAL 14 DAY, '15:00:00') + INTERVAL @appOffsetHours HOUR
  UNION ALL SELECT TIMESTAMP(@today - INTERVAL 7 DAY, '15:00:00') + INTERVAL @appOffsetHours HOUR
       ) AS h
 WHERE @meilingHistory = 1
   AND NOT EXISTS (SELECT 1 FROM visit v WHERE v.elder_id = @graceElder AND v.scheduled_start = h.starts);

-- Her note on each, for the periodic reports. The visits are a week apart, so
-- the day of the year picks a different note for each of the five.
INSERT INTO visit_task (visit_id, name, status, caregiver_note, completed_at)
SELECT v.id, 'Afternoon care', 'DONE',
       ELT(1 + MOD(DAYOFYEAR(v.scheduled_start), 5),
           'Walked to the market and back with her cane. Steady, stopped once to rest.',
           'Helped her wash her hair on the shower chair. She says the grab bar makes her feel safer.',
           'Made fish porridge for dinner and left a bowl in the fridge. She ate a little while I was there.',
           'Put the evening blood pressure tablets out in her pill box and watched her take them.',
           'Folded the laundry together and talked about her grandson''s visit at the weekend.'),
       v.scheduled_start + INTERVAL 45 MINUTE
  FROM visit v
 WHERE v.elder_id = @graceElder
   AND v.caregiver_id = @meilingCg
   AND v.status = 'VERIFIED'
   AND NOT EXISTS (SELECT 1 FROM visit_task t WHERE t.visit_id = v.id);

-- Two spot checks concluded at Chua's visits a fortnight ago: Hui Min's needed
-- improvement, Farah's met the standard. The re-rostering search reads both
-- through its spot-check rule, which looks back 90 days.
SET @chuaHistory := (SELECT CASE
        WHEN @chuaElder IS NULL OR @huiminCg IS NULL OR @farahCg IS NULL OR @lilyFamily IS NULL THEN 0
        WHEN EXISTS (SELECT 1 FROM spot_check WHERE caregiver_id IN (@huiminCg, @farahCg)
                                              AND outcome = 'COMPLETED') THEN 0
        ELSE 1 END);
SET @huiminVisitStart := TIMESTAMP(@today - INTERVAL 14 DAY, '14:00:00') + INTERVAL @appOffsetHours HOUR;
SET @farahVisitStart  := TIMESTAMP(@today - INTERVAL 12 DAY, '10:00:00') + INTERVAL @appOffsetHours HOUR;

INSERT INTO visit (elder_id, caregiver_id, service_type, scheduled_start, scheduled_end,
                   checked_in_at, checked_out_at, status)
SELECT @chuaElder, h.caregiver_id, 'Rehabilitation exercises', h.starts, h.starts + INTERVAL 1 HOUR,
       h.starts + INTERVAL 3 MINUTE, h.starts + INTERVAL 57 MINUTE, 'VERIFIED'
  FROM (
        SELECT @huiminCg AS caregiver_id, @huiminVisitStart AS starts
  UNION ALL SELECT @farahCg, @farahVisitStart
       ) AS h
 WHERE @chuaHistory = 1
   AND NOT EXISTS (SELECT 1 FROM visit v WHERE v.elder_id = @chuaElder AND v.scheduled_start = h.starts);

SET @huiminVisit := (SELECT id FROM visit WHERE elder_id = @chuaElder AND caregiver_id = @huiminCg
                        AND scheduled_start = @huiminVisitStart ORDER BY id LIMIT 1);
SET @farahVisit  := (SELECT id FROM visit WHERE elder_id = @chuaElder AND caregiver_id = @farahCg
                        AND scheduled_start = @farahVisitStart ORDER BY id LIMIT 1);

INSERT INTO visit_task (visit_id, name, status, caregiver_note, completed_at)
SELECT notes.visit_id, 'Range-of-motion exercises', 'DONE', notes.note, notes.done
  FROM (
        SELECT @huiminVisit AS visit_id,
               'Arm and leg stretches. Mdm Chua was tired today, so we kept it short.' AS note,
               @huiminVisitStart + INTERVAL 40 MINUTE AS done
  UNION ALL SELECT @farahVisit,
               'Full set of stretches and sit-to-stand practice with the transfer belt. Good effort.',
               @farahVisitStart + INTERVAL 40 MINUTE
       ) AS notes
 WHERE notes.visit_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM visit_task t WHERE t.visit_id = notes.visit_id);

INSERT INTO spot_check (elder_id, caregiver_id, visit_id, raised_by_user_id, approving_family_member_id,
                        proposed_time, reason, approval_status, decided_at, finding, result, outcome,
                        caregiver_response, checked_at, created_at)
SELECT @chuaElder, c.caregiver_id, c.visit_id, @alice, @lilyFamily, c.starts, c.reason, 'APPROVED',
       c.decided_at, c.finding, c.result, 'COMPLETED', c.response, c.checked_at, c.created_at
  FROM (
        SELECT @huiminCg AS caregiver_id, @huiminVisit AS visit_id, @huiminVisitStart AS starts,
               'Routine check after Mdm Chua''s exercise programme changed' AS reason,
               @huiminVisitStart - INTERVAL 2 DAY + INTERVAL 320 MINUTE AS decided_at,
               'Skipped the blood pressure reading the plan asks for before the exercises. Talked it through with her afterwards.' AS finding,
               'NEEDS_IMPROVEMENT' AS result,
               'The cuff was not in my bag that day. I carry a spare one now.' AS response,
               @huiminVisitStart + INTERVAL 65 MINUTE AS checked_at,
               @huiminVisitStart - INTERVAL 3 DAY - INTERVAL 180 MINUTE AS created_at
  UNION ALL SELECT @farahCg, @farahVisit, @farahVisitStart,
               'Routine quality check',
               @farahVisitStart - INTERVAL 3 DAY + INTERVAL 520 MINUTE,
               'Used the transfer belt correctly and explained each exercise before starting. Mdm Chua was comfortable throughout.',
               'MEETS_STANDARD',
               NULL,
               @farahVisitStart + INTERVAL 62 MINUTE,
               @farahVisitStart - INTERVAL 3 DAY
       ) AS c
 WHERE @chuaHistory = 1
   AND c.visit_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM spot_check s WHERE s.visit_id = c.visit_id);

-- --------------------------------------------------------------- absences ---
-- Aisha's leave and the visits it vacates. Laid out when there is none yet,
-- when the last one's days are over, or when it has been re-rostered already;
-- always starting after the last one ends, so two never overlap.
SET @aishaLastEnd := (SELECT MAX(end_date) FROM absence_report
                       WHERE caregiver_id = @aishaCg AND status <> 'REJECTED');
SET @aishaLastId  := (SELECT id FROM absence_report
                       WHERE caregiver_id = @aishaCg AND status <> 'REJECTED'
                       ORDER BY end_date DESC, id DESC LIMIT 1);
SET @freshAbsence := (SELECT CASE
        WHEN @aishaCg IS NULL OR @graceElder IS NULL THEN 0
        WHEN @aishaLastId IS NULL THEN 1
        WHEN DATE(@aishaLastEnd) < CURRENT_DATE() THEN 1
        WHEN EXISTS (SELECT 1 FROM roster_change WHERE absence_id = @aishaLastId) THEN 1
        ELSE 0 END);
-- User variables hold dates as text; DATE() turns them back, so the two sides compare as dates.
SET @firstDay := GREATEST(CURRENT_DATE() + INTERVAL 2 DAY,
                          COALESCE(DATE(@aishaLastEnd) + INTERVAL 1 DAY, CURRENT_DATE()));

INSERT INTO absence_report (caregiver_id, reviewed_by_user_id, type, start_date, end_date, reason, status, created_at)
SELECT @aishaCg, @alice, 'SICK', @firstDay, @firstDay + INTERVAL 1 DAY,
       'Minor operation, two days of medical leave', 'APPROVED', @nowStored - INTERVAL 3 HOUR
  FROM DUAL
 WHERE @freshAbsence = 1;

-- Siew Lan is away the same two days, so the search has a second person on leave
-- to exclude besides Aisha.
INSERT INTO absence_report (caregiver_id, reviewed_by_user_id, type, start_date, end_date, reason, status, created_at)
SELECT @siewlanCg, @alice, 'ANNUAL', @firstDay, @firstDay + INTERVAL 1 DAY,
       'Family trip to Malacca', 'APPROVED', @nowStored - INTERVAL 9 DAY
  FROM DUAL
 WHERE @freshAbsence = 1
   AND @siewlanCg IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM absence_report a
                    WHERE a.caregiver_id = @siewlanCg AND a.status <> 'REJECTED'
                      AND a.start_date <= @firstDay + INTERVAL 1 DAY AND a.end_date >= @firstDay);

-- Aisha's three visits, and Joseph's visit to Chua at the time of Grace's first
-- one, which is what keeps him off it.
SET @graceFirst  := TIMESTAMP(@firstDay, '10:00:00') + INTERVAL @appOffsetHours HOUR;
SET @chuaFirst   := TIMESTAMP(@firstDay, '14:00:00') + INTERVAL @appOffsetHours HOUR;
SET @graceSecond := TIMESTAMP(@firstDay + INTERVAL 1 DAY, '10:00:00') + INTERVAL @appOffsetHours HOUR;

INSERT INTO visit (elder_id, caregiver_id, service_type, scheduled_start, scheduled_end, status)
SELECT p.elder_id, p.caregiver_id, p.service_type, p.starts, p.starts + INTERVAL 1 HOUR, 'SCHEDULED'
  FROM (
        SELECT @graceElder AS elder_id, @aishaCg AS caregiver_id, 'Personal care' AS service_type, @graceFirst AS starts
  UNION ALL SELECT @chuaElder,  @aishaCg,  'Rehabilitation exercises', @chuaFirst
  UNION ALL SELECT @graceElder, @aishaCg,  'Personal care',            @graceSecond
  UNION ALL SELECT @chuaElder,  @josephCg, 'Meal preparation',         @graceFirst
       ) AS p
 WHERE @freshAbsence = 1
   AND p.elder_id IS NOT NULL
   AND p.caregiver_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM visit v WHERE v.elder_id = p.elder_id AND v.scheduled_start = p.starts);

-- Nora's own request, waiting for a manager, with the visit approving it vacates.
-- A new one only when she has no request still ahead of her waiting.
SET @noraWaiting := (SELECT COUNT(*) FROM absence_report
                      WHERE caregiver_id = @noraCg AND status = 'PENDING' AND start_date >= @today);
SET @noraLastEnd := (SELECT MAX(end_date) FROM absence_report
                      WHERE caregiver_id = @noraCg AND status <> 'REJECTED');
SET @noraDay := GREATEST(CURRENT_DATE() + INTERVAL 6 DAY,
                         COALESCE(DATE(@noraLastEnd) + INTERVAL 1 DAY, CURRENT_DATE()));
SET @noraVisitStart := TIMESTAMP(@noraDay, '14:00:00') + INTERVAL @appOffsetHours HOUR;

INSERT INTO absence_report (caregiver_id, type, start_date, end_date, reason, status, created_at)
SELECT @noraCg, 'ANNUAL', @noraDay, @noraDay, 'My daughter''s graduation', 'PENDING', @nowStored - INTERVAL 5 HOUR
  FROM DUAL
 WHERE @noraCg IS NOT NULL
   AND @noraWaiting = 0;

INSERT INTO visit (elder_id, caregiver_id, service_type, scheduled_start, scheduled_end, status)
SELECT @graceElder, @noraCg, 'Personal care', @noraVisitStart, @noraVisitStart + INTERVAL 1 HOUR, 'SCHEDULED'
  FROM DUAL
 WHERE @noraCg IS NOT NULL
   AND @graceElder IS NOT NULL
   AND @noraWaiting = 0
   AND NOT EXISTS (SELECT 1 FROM visit v WHERE v.elder_id = @graceElder AND v.scheduled_start = @noraVisitStart);

-- ------------------------------------------------------------ spot checks ---
-- One check waiting for Fiona's consent, on a visit of Mei Ling's, with the
-- message Fiona would have been sent. A new one only when none is waiting.
SET @consentWaiting := (SELECT COUNT(*) FROM spot_check
                         WHERE elder_id = @graceElder AND approval_status = 'PENDING_APPROVAL'
                           AND outcome IS NULL AND proposed_time > @nowStored);
SET @consentVisitStart := TIMESTAMP(@today + INTERVAL 4 DAY, '15:00:00') + INTERVAL @appOffsetHours HOUR;
SET @consentReason := 'Routine quality check: Mei Ling has looked after Grace for five weeks';

INSERT INTO visit (elder_id, caregiver_id, service_type, scheduled_start, scheduled_end, status)
SELECT @graceElder, @meilingCg, 'Personal care', @consentVisitStart, @consentVisitStart + INTERVAL 1 HOUR, 'SCHEDULED'
  FROM DUAL
 WHERE @consentWaiting = 0
   AND @graceElder IS NOT NULL
   AND @meilingCg IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM visit v WHERE v.elder_id = @graceElder AND v.scheduled_start = @consentVisitStart);

SET @consentVisit := (SELECT id FROM visit WHERE elder_id = @graceElder AND caregiver_id = @meilingCg
                         AND scheduled_start = @consentVisitStart AND status = 'SCHEDULED' ORDER BY id LIMIT 1);

INSERT INTO spot_check (elder_id, caregiver_id, visit_id, raised_by_user_id, proposed_time, reason,
                        approval_status, created_at)
SELECT @graceElder, @meilingCg, @consentVisit, @alice, @consentVisitStart, @consentReason,
       'PENDING_APPROVAL', @nowStored - INTERVAL 2 HOUR
  FROM DUAL
 WHERE @consentWaiting = 0
   AND @consentVisit IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM spot_check s WHERE s.visit_id = @consentVisit);

SET @consentCheck := (SELECT id FROM spot_check WHERE visit_id = @consentVisit
                         AND approval_status = 'PENDING_APPROVAL' ORDER BY id DESC LIMIT 1);
SET @consentWhen := DATE_FORMAT(@consentVisitStart - INTERVAL @appOffsetHours HOUR, '%a %e %b, %H:%i');

INSERT INTO notification (recipient_user_id, event_type, channel, title, body, resource_type, resource_id,
                          status, created_at)
SELECT @fiona, 'SPOT_CHECK_REQUESTED', 'IN_APP',
       CONCAT('May a manager watch the visit on ', @consentWhen, '?'),
       CONCAT('To check the quality of care, a manager would like to be present at Grace Wee''s visit on ',
              @consentWhen, ': ', @consentReason, '. Nobody comes to watch unless you agree.'),
       'SPOT_CHECK', @consentCheck, 'PENDING', @nowStored - INTERVAL 2 HOUR
  FROM DUAL
 WHERE @consentCheck IS NOT NULL
   AND @fiona IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM notification n WHERE n.resource_type = 'SPOT_CHECK' AND n.resource_id = @consentCheck);

-- One check Fiona agreed to yesterday, on a visit of Farah's, for the manager to
-- record a conclusion on. A new one only when none is waiting to be recorded.
SET @agreedWaiting := (SELECT COUNT(*) FROM spot_check
                        WHERE elder_id = @graceElder AND approval_status = 'APPROVED'
                          AND outcome IS NULL AND proposed_time > @nowStored);
SET @agreedVisitStart := TIMESTAMP(@today + INTERVAL 5 DAY, '11:30:00') + INTERVAL @appOffsetHours HOUR;

INSERT INTO visit (elder_id, caregiver_id, service_type, scheduled_start, scheduled_end, status)
SELECT @graceElder, @farahCg, 'Personal care', @agreedVisitStart, @agreedVisitStart + INTERVAL 1 HOUR, 'SCHEDULED'
  FROM DUAL
 WHERE @agreedWaiting = 0
   AND @graceElder IS NOT NULL
   AND @farahCg IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM visit v WHERE v.elder_id = @graceElder AND v.scheduled_start = @agreedVisitStart);

SET @agreedVisit := (SELECT id FROM visit WHERE elder_id = @graceElder AND caregiver_id = @farahCg
                        AND scheduled_start = @agreedVisitStart AND status = 'SCHEDULED' ORDER BY id LIMIT 1);

INSERT INTO spot_check (elder_id, caregiver_id, visit_id, raised_by_user_id, approving_family_member_id,
                        proposed_time, reason, approval_status, decided_at, created_at)
SELECT @graceElder, @farahCg, @agreedVisit, @alice, @fionaFamily, @agreedVisitStart,
       'Check the shower-chair routine set up after Grace''s fall in September', 'APPROVED',
       @nowStored - INTERVAL 20 HOUR, @nowStored - INTERVAL 26 HOUR
  FROM DUAL
 WHERE @agreedWaiting = 0
   AND @agreedVisit IS NOT NULL
   AND @fionaFamily IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM spot_check s WHERE s.visit_id = @agreedVisit);

-- ------------------------------------------------------------ assignments ---
-- Every visit above that is still to come was rostered by a manager, and the
-- re-rostering records who replaced whom against these rows. Visits the
-- application has already moved or reassigned have rows of their own.
INSERT INTO visit_assignment (visit_id, caregiver_id, assigned_by_user_id, status, reason, assigned_at)
SELECT v.id, v.caregiver_id, @alice, 'ACTIVE', 'Weekly roster', @nowStored - INTERVAL 7 DAY
  FROM visit v
 WHERE v.caregiver_id IN (@aishaCg, @meilingCg, @farahCg, @huiminCg, @siewlanCg, @josephCg, @noraCg)
   AND v.status = 'SCHEDULED'
   AND v.care_plan_id IS NULL
   AND v.scheduled_start > @nowStored
   AND NOT EXISTS (SELECT 1 FROM visit_assignment a WHERE a.visit_id = v.id);

COMMIT;
