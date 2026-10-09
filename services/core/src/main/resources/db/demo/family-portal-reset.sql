-- =====================================================================
-- Re-date the family portal demo (family-portal.sql)
-- =====================================================================
--
-- LOCAL ONLY. family-portal.sql lays out visits and reports around the day it
-- is first loaded and never moves them. Run this, then family-portal.sql again,
-- to rebuild them around today:
--
--   docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" carelink' \
--       < services/core/src/main/resources/db/demo/family-portal-reset.sql
--
-- If the backend runs from ./mvnw against a MySQL installed on this machine
-- (application.yml's default localhost:3306), load it there instead:
--
--   mysql -h127.0.0.1 -ucarelink -pcarelink carelink < services/core/src/main/resources/db/demo/family-portal-reset.sql
--
-- Removes only the visits (and what hangs off them), reports and caregiver
-- credentials belonging to the two demo elders and two demo caregivers.
-- Accounts, profiles, elders, bindings and intake applications are kept.
--
-- If anything else now points at one of these visits (an incident raised on it,
-- a rostering run that considered it), the delete fails and the transaction
-- rolls back, leaving everything as it was.
-- =====================================================================

START TRANSACTION;

CREATE TEMPORARY TABLE demo_fam_elder AS
SELECT e.id
  FROM elder e
  JOIN elder_family_binding b ON b.elder_id = e.id
  JOIN family_member fm ON fm.id = b.family_member_id
  JOIN app_user u ON u.id = fm.user_id
 WHERE u.username = 'demo-fam-wei'
   AND e.full_name IN ('Lim Boon Huat', 'Tan Siew Lan');

CREATE TEMPORARY TABLE demo_fam_visit_id AS
SELECT v.id FROM visit v WHERE v.elder_id IN (SELECT id FROM demo_fam_elder);

-- Rows from family-changes-spot-checks.sql, which reference these visits.
DELETE FROM roster_change          WHERE visit_id IN (SELECT id FROM demo_fam_visit_id);
DELETE FROM rostering_candidate_check
 WHERE rostering_candidate_id IN (SELECT id FROM rostering_candidate
                                   WHERE visit_id IN (SELECT id FROM demo_fam_visit_id));
DELETE FROM rostering_candidate    WHERE visit_id IN (SELECT id FROM demo_fam_visit_id);
DELETE FROM spot_check             WHERE elder_id IN (SELECT id FROM demo_fam_elder);

DELETE FROM visit_task             WHERE visit_id IN (SELECT id FROM demo_fam_visit_id);
DELETE FROM vital_sign             WHERE visit_id IN (SELECT id FROM demo_fam_visit_id);
DELETE FROM visit_evidence         WHERE visit_id IN (SELECT id FROM demo_fam_visit_id);
DELETE FROM visit_state_transition WHERE visit_id IN (SELECT id FROM demo_fam_visit_id);
DELETE FROM visit_assignment       WHERE visit_id IN (SELECT id FROM demo_fam_visit_id);
DELETE FROM elder_confirmation     WHERE visit_id IN (SELECT id FROM demo_fam_visit_id);
DELETE FROM visit                  WHERE id       IN (SELECT id FROM demo_fam_visit_id);

DELETE FROM report_amendment
 WHERE report_id IN (SELECT r.id FROM report r WHERE r.elder_id IN (SELECT id FROM demo_fam_elder));
DELETE FROM report WHERE elder_id IN (SELECT id FROM demo_fam_elder);

-- Credentials are dated too (expiring in 20 days, expired 10 days ago...).
DELETE FROM credential
 WHERE certificate_no LIKE 'DEMOFAM-%'
   AND caregiver_id IN (SELECT c.id FROM caregiver c JOIN app_user u ON u.id = c.user_id
                         WHERE u.username IN ('demo-fam-cg-ming', 'demo-fam-cg-siti'));

DROP TEMPORARY TABLE demo_fam_visit_id;
DROP TEMPORARY TABLE demo_fam_elder;

COMMIT;
