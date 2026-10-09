-- =====================================================================
-- Demonstration data: visit changes and spot checks for the family portal
-- (UC-MG04 steps 4-5, UC-MG08)
-- =====================================================================
--
-- LOCAL ONLY. NOT A FLYWAY MIGRATION, for the reasons given at the top of
-- demo-seed.sql. Load family-portal.sql first: everything here hangs off its
-- elders (Lim Boon Huat, Tan Siew Lan), its caregivers (demo-fam-cg-ming,
-- demo-fam-cg-siti) and its visits. Then:
--
--   mysql -h127.0.0.1 -ucarelink -pcarelink carelink < services/core/src/main/resources/db/demo/family-changes-spot-checks.sql
--
-- or, against the compose database:
--
--   docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" carelink' \
--       < services/core/src/main/resources/db/demo/family-changes-spot-checks.sql
--
-- Sign in as demo-fam-wei (Demo#2026) and open Visit changes and Spot checks.
--
--   Visit changes   Chen Ming is on medical leave: two of Lim Boon Huat's
--                   visits wait for the family to choose who comes, and an
--                   unassigned one nobody could cover sits with a manager.
--                   Siti Rahmah's earlier emergency leave left one visit
--                   settled by the default plan and one the family skipped.
--   Spot checks     One request waiting for consent, one agreed and booked,
--                   two concluded (meets the standard / needs improvement),
--                   and one the family declined.
--
-- Only rows are added; no existing visit is changed, so a REPLACED change's
-- visit still shows its original caregiver on the schedule.
--
-- The answer-by time of a waiting change is the evening before its visit,
-- and the visits chosen are at least two days out. Once that time passes, the
-- backend's sweep runs the default plan for real (it reassigns the visit), so
-- run this file again to bring the waiting changes back.
--
-- SAFE TO RUN AGAIN. It first removes every absence of the two demo
-- caregivers (with its runs, candidates and changes) and every spot check of
-- the two demo elders, then writes them fresh, relative to today.
-- =====================================================================

-- See TIMESTAMPS in demo-seed.sql: 0 for a backend started with ./mvnw on a
-- machine set to Singapore time, 8 for the compose container or staging.
SET @appOffsetHours := 0;

SET time_zone = '+08:00';
SET @today := CURRENT_DATE();
SET @now   := NOW() + INTERVAL @appOffsetHours HOUR;
SET @soon  := TIMESTAMP(@today + INTERVAL 2 DAY, '00:00:00') + INTERVAL @appOffsetHours HOUR;

START TRANSACTION;

SET @eBoon := (SELECT MIN(id) FROM elder WHERE full_name = 'Lim Boon Huat');
SET @eSiew := (SELECT MIN(id) FROM elder WHERE full_name = 'Tan Siew Lan');
SET @cMing := (SELECT c.id FROM caregiver c JOIN app_user u ON u.id = c.user_id WHERE u.username = 'demo-fam-cg-ming');
SET @cSiti := (SELECT c.id FROM caregiver c JOIN app_user u ON u.id = c.user_id WHERE u.username = 'demo-fam-cg-siti');
SET @uWei  := (SELECT id FROM app_user WHERE username = 'demo-fam-wei');
SET @fWei  := (SELECT id FROM family_member WHERE user_id = @uWei);
SET @uMgr  := (SELECT id FROM app_user WHERE username = 'manager@carelink.sg');

-- ------------------------------------------------------------ clear last run ---
CREATE TEMPORARY TABLE demo_fcs_absence AS
SELECT id FROM absence_report WHERE caregiver_id IN (@cMing, @cSiti);

CREATE TEMPORARY TABLE demo_fcs_run AS
SELECT id FROM rostering_run WHERE absence_id IN (SELECT id FROM demo_fcs_absence);

DELETE FROM roster_change WHERE absence_id IN (SELECT id FROM demo_fcs_absence);
DELETE FROM rostering_candidate_check
 WHERE rostering_candidate_id IN (SELECT id FROM rostering_candidate
                                   WHERE rostering_run_id IN (SELECT id FROM demo_fcs_run));
DELETE FROM rostering_candidate WHERE rostering_run_id IN (SELECT id FROM demo_fcs_run);
DELETE FROM rostering_run       WHERE id IN (SELECT id FROM demo_fcs_run);
UPDATE visit SET absence_id = NULL WHERE absence_id IN (SELECT id FROM demo_fcs_absence);
DELETE FROM absence_report      WHERE id IN (SELECT id FROM demo_fcs_absence);
DELETE FROM spot_check          WHERE elder_id IN (@eBoon, @eSiew);

DROP TEMPORARY TABLE demo_fcs_run;
DROP TEMPORARY TABLE demo_fcs_absence;

-- ------------------------------------------------------------ pick the visits ---
-- Waiting for the family: Chen Ming's next two visits to Lim Boon Huat, two or more days out.
SET @vAsk1 := (SELECT id FROM visit WHERE elder_id = @eBoon AND caregiver_id = @cMing
                  AND status = 'SCHEDULED' AND scheduled_start >= @soon
                ORDER BY scheduled_start LIMIT 1);
SET @vAsk2 := (SELECT id FROM visit WHERE elder_id = @eBoon AND caregiver_id = @cMing
                  AND status = 'SCHEDULED'
                  AND scheduled_start > (SELECT scheduled_start FROM visit WHERE id = @vAsk1)
                ORDER BY scheduled_start LIMIT 1);
-- Nobody free: the upcoming visit that has no caregiver.
SET @vOpen := (SELECT id FROM visit WHERE elder_id = @eBoon AND caregiver_id IS NULL
                  AND status = 'SCHEDULED' AND scheduled_start >= @now
                ORDER BY scheduled_start LIMIT 1);
-- Settled: a past visit the default plan gave to Chen Ming, and the cancelled one the family skipped.
SET @vSwap := (SELECT id FROM visit WHERE elder_id = @eSiew AND status = 'VERIFIED' AND scheduled_start < @now
                ORDER BY scheduled_start DESC LIMIT 1);
SET @vSkip := (SELECT id FROM visit WHERE elder_id = @eBoon AND status = 'CANCELLED' AND scheduled_start < @now
                ORDER BY scheduled_start DESC LIMIT 1);

-- ------------------------------------------------------------------ absences ---
INSERT INTO absence_report (caregiver_id, reviewed_by_user_id, type, start_date, end_date, reason, status)
SELECT @cMing, @uMgr, 'SICK',
       DATE(MIN(scheduled_start)), DATE(MAX(scheduled_start)), 'Medical leave', 'APPROVED'
  FROM visit WHERE id IN (@vAsk1, @vAsk2, @vOpen)
HAVING COUNT(*) > 0;
SET @aMing := IF(ROW_COUNT() > 0, LAST_INSERT_ID(), NULL);

INSERT INTO absence_report (caregiver_id, reviewed_by_user_id, type, start_date, end_date, reason, status,
                            coverage_confirmed_at, coverage_confirmed_by_user_id)
SELECT @cSiti, @uMgr, 'EMERGENCY',
       DATE(MIN(scheduled_start)), DATE(MAX(scheduled_start)), 'Family emergency', 'APPROVED',
       MAX(scheduled_start), @uMgr
  FROM visit WHERE id IN (@vSwap, @vSkip)
HAVING COUNT(*) > 0;
SET @aSiti := IF(ROW_COUNT() > 0, LAST_INSERT_ID(), NULL);

INSERT INTO rostering_run (trigger_type, absence_id, objective, requested_by_user_id, status,
                           visits_total, visits_covered, continuity_kept, ran_at)
SELECT 'ABSENCE', @aMing, 'CONTINUITY', @uMgr, 'PROPOSED', 3, 2, 2, @now - INTERVAL 2 HOUR
 WHERE @aMing IS NOT NULL;
SET @runMing := IF(ROW_COUNT() > 0, LAST_INSERT_ID(), NULL);

INSERT INTO rostering_run (trigger_type, absence_id, objective, requested_by_user_id, status,
                           visits_total, visits_covered, continuity_kept, ran_at, committed_at)
SELECT 'ABSENCE', @aSiti, 'CONTINUITY', @uMgr, 'COMMITTED', 2, 1, 0, start_date - INTERVAL 1 DAY,
       start_date - INTERVAL 1 DAY
  FROM absence_report WHERE id = @aSiti;
SET @runSiti := IF(ROW_COUNT() > 0, LAST_INSERT_ID(), NULL);

-- ---------------------------------------------- suggestions offered to the family ---
-- Siti Rahmah first (she knows Lim Boon Huat), then up to two other caregivers who are free.
CREATE TEMPORARY TABLE demo_fcs_option (caregiver_id BIGINT, option_rank INT, score DECIMAL(6,2), reason VARCHAR(120));
INSERT INTO demo_fcs_option VALUES (@cSiti, 1, 92.50, 'Has cared for Lim Boon Huat before');
INSERT INTO demo_fcs_option
SELECT id, 2, 78.00, 'Works in Lim Boon Huat''s sector'
  FROM caregiver WHERE status = 'AVAILABLE' AND id NOT IN (@cMing, @cSiti) ORDER BY id LIMIT 1;
INSERT INTO demo_fcs_option
SELECT id, 3, 64.00, 'Free at that time'
  FROM caregiver WHERE status = 'AVAILABLE' AND id NOT IN (@cMing, @cSiti) ORDER BY id LIMIT 1 OFFSET 1;

INSERT INTO rostering_candidate (rostering_run_id, visit_id, caregiver_id, option_rank, score, outcome, match_reason)
SELECT @runMing, v.id, o.caregiver_id, o.option_rank, o.score, 'SUGGESTED', o.reason
  FROM demo_fcs_option o
  JOIN visit v ON v.id IN (@vAsk1, @vAsk2)
 WHERE @runMing IS NOT NULL AND o.caregiver_id IS NOT NULL;

DROP TEMPORARY TABLE demo_fcs_option;

-- ------------------------------------------------------------ roster changes ---
-- Waiting: the family answers by 18:00 the evening before (for a morning visit).
INSERT INTO roster_change (absence_id, visit_id, elder_id, original_caregiver_id, visit_start, visit_end,
                           rostering_run_id, proposed_caregiver_id, status, respond_by, created_at, updated_at)
SELECT @aMing, v.id, v.elder_id, @cMing, v.scheduled_start, v.scheduled_end,
       @runMing, @cSiti, 'AWAITING_FAMILY', v.scheduled_start - INTERVAL 15 HOUR, @now - INTERVAL 2 HOUR,
       @now - INTERVAL 2 HOUR
  FROM visit v WHERE v.id IN (@vAsk1, @vAsk2) AND @aMing IS NOT NULL;

-- Uncovered: nobody was free, so a manager has it.
INSERT INTO roster_change (absence_id, visit_id, elder_id, original_caregiver_id, visit_start, visit_end,
                           rostering_run_id, status, note, created_at, updated_at)
SELECT @aMing, v.id, v.elder_id, @cMing, v.scheduled_start, v.scheduled_end,
       @runMing, 'UNCOVERED', 'Nobody was free to cover this visit', @now - INTERVAL 2 HOUR, @now - INTERVAL 2 HOUR
  FROM visit v WHERE v.id = @vOpen AND @aMing IS NOT NULL;

-- Settled by the default plan: the family did not answer in time.
INSERT INTO roster_change (absence_id, visit_id, elder_id, original_caregiver_id, visit_start, visit_end,
                           rostering_run_id, proposed_caregiver_id, status, outcome, decided_by,
                           assigned_caregiver_id, respond_by, decided_at, note, created_at, updated_at)
SELECT @aSiti, v.id, v.elder_id, @cSiti, v.scheduled_start, v.scheduled_end,
       @runSiti, @cMing, 'RESOLVED', 'REPLACED', 'DEFAULT_PLAN',
       @cMing, v.scheduled_start - INTERVAL 15 HOUR, v.scheduled_start - INTERVAL 15 HOUR,
       'The family did not answer in time; the suggested caregiver went',
       v.scheduled_start - INTERVAL 1 DAY, v.scheduled_start - INTERVAL 15 HOUR
  FROM visit v WHERE v.id = @vSwap AND @aSiti IS NOT NULL;

-- Skipped at the family's request.
INSERT INTO roster_change (absence_id, visit_id, elder_id, original_caregiver_id, visit_start, visit_end,
                           rostering_run_id, proposed_caregiver_id, status, outcome, decided_by,
                           decided_by_user_id, respond_by, decided_at, note, created_at, updated_at)
SELECT @aSiti, v.id, v.elder_id, @cSiti, v.scheduled_start, v.scheduled_end,
       @runSiti, @cMing, 'RESOLVED', 'SKIPPED', 'FAMILY',
       @uWei, v.scheduled_start - INTERVAL 15 HOUR, v.scheduled_start - INTERVAL 20 HOUR,
       'Skipped at the family''s request',
       v.scheduled_start - INTERVAL 1 DAY, v.scheduled_start - INTERVAL 20 HOUR
  FROM visit v WHERE v.id = @vSkip AND @aSiti IS NOT NULL;

-- --------------------------------------------------------------- spot checks ---
-- Waiting for consent: Tan Siew Lan's next visit.
INSERT INTO spot_check (elder_id, caregiver_id, visit_id, raised_by_user_id, proposed_time, reason,
                        approval_status, created_at)
SELECT v.elder_id, v.caregiver_id, v.id, @uMgr, v.scheduled_start,
       'Routine quality check of her morning routine and memory activities', 'PENDING_APPROVAL', @now - INTERVAL 3 HOUR
  FROM visit v
 WHERE v.elder_id = @eSiew AND v.status = 'SCHEDULED' AND v.caregiver_id IS NOT NULL AND v.scheduled_start >= @now
 ORDER BY v.scheduled_start LIMIT 1;

-- Agreed and booked: Lim Boon Huat's next visit with Siti Rahmah.
INSERT INTO spot_check (elder_id, caregiver_id, visit_id, raised_by_user_id, approving_family_member_id,
                        proposed_time, reason, approval_status, decided_at, created_at)
SELECT v.elder_id, v.caregiver_id, v.id, @uMgr, @fWei, v.scheduled_start,
       'New vitals checklist: confirming it is followed at home', 'APPROVED',
       @now - INTERVAL 1 DAY, @now - INTERVAL 2 DAY
  FROM visit v
 WHERE v.elder_id = @eBoon AND v.caregiver_id = @cSiti AND v.status = 'SCHEDULED' AND v.scheduled_start >= @now
 ORDER BY v.scheduled_start LIMIT 1;

-- Concluded, meets the standard.
INSERT INTO spot_check (elder_id, caregiver_id, visit_id, raised_by_user_id, approving_family_member_id,
                        proposed_time, reason, approval_status, decided_at, finding, result, outcome,
                        checked_at, created_at)
SELECT v.elder_id, v.caregiver_id, v.id, @uMgr, @fWei, v.scheduled_start,
       'Routine quality check', 'APPROVED', v.scheduled_start - INTERVAL 2 DAY,
       'Warm and patient; followed every step of the care plan and explained each one', 'MEETS_STANDARD', 'COMPLETED',
       v.scheduled_start + INTERVAL 1 HOUR, v.scheduled_start - INTERVAL 3 DAY
  FROM visit v
 WHERE v.elder_id = @eBoon AND v.caregiver_id = @cSiti AND v.status = 'VERIFIED' AND v.scheduled_start < @now
 ORDER BY v.scheduled_start LIMIT 1;

-- Concluded, needs improvement.
INSERT INTO spot_check (elder_id, caregiver_id, visit_id, raised_by_user_id, approving_family_member_id,
                        proposed_time, reason, approval_status, decided_at, finding, result, outcome,
                        checked_at, created_at)
SELECT v.elder_id, v.caregiver_id, v.id, @uMgr, @fWei, v.scheduled_start,
       'Follow-up after a late arrival was reported', 'APPROVED', v.scheduled_start - INTERVAL 2 DAY,
       'Care itself was good, but vitals were only written down after leaving the home', 'NEEDS_IMPROVEMENT', 'COMPLETED',
       v.scheduled_start + INTERVAL 1 HOUR, v.scheduled_start - INTERVAL 3 DAY
  FROM visit v
 WHERE v.elder_id = @eBoon AND v.caregiver_id = @cMing AND v.status = 'VERIFIED' AND v.scheduled_start < @now
 ORDER BY v.scheduled_start LIMIT 1;

-- Declined by the family, with their reason.
INSERT INTO spot_check (elder_id, caregiver_id, visit_id, raised_by_user_id, approving_family_member_id,
                        proposed_time, reason, approval_status, decided_at, closing_reason, created_at)
SELECT v.elder_id, v.caregiver_id, v.id, @uMgr, @fWei, v.scheduled_start,
       'Routine quality check', 'REJECTED', v.scheduled_start - INTERVAL 1 DAY,
       'My mother was unwell that week and would rather not have visitors', v.scheduled_start - INTERVAL 2 DAY
  FROM visit v
 WHERE v.elder_id = @eSiew AND v.caregiver_id IS NOT NULL AND v.scheduled_start < @now
   AND v.status <> 'CANCELLED'
 ORDER BY v.scheduled_start DESC LIMIT 1;

COMMIT;

SELECT status, outcome, COUNT(*) AS roster_changes
  FROM roster_change WHERE elder_id IN (@eBoon, @eSiew) GROUP BY status, outcome;
SELECT approval_status, outcome, result, COUNT(*) AS spot_checks
  FROM spot_check WHERE elder_id IN (@eBoon, @eSiew) GROUP BY approval_status, outcome, result;
