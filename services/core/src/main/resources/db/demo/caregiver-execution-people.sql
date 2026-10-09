-- LOCAL ISOLATED DEMO ONLY. Synthetic identities/binding, not Visits or execution facts.
-- Password for these fictional accounts: Demo#2026. Never run in staging/production.
START TRANSACTION;
INSERT IGNORE INTO app_user(username,password_hash,display_name) VALUES
 ('demo-exec-cg-a','{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm','Execution Demo Caregiver A'),
 ('demo-exec-cg-b','{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm','Execution Demo Caregiver B'),
 ('demo-exec-manager','{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm','Execution Demo Manager'),
 ('demo-exec-family','{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm','Execution Demo Family');
INSERT IGNORE INTO user_role(user_id,role)
 SELECT id, CASE WHEN username='demo-exec-manager' THEN 'MANAGER'
 WHEN username='demo-exec-family' THEN 'FAMILY' ELSE 'CAREGIVER' END
 FROM app_user WHERE username IN ('demo-exec-cg-a','demo-exec-cg-b','demo-exec-manager','demo-exec-family');
INSERT IGNORE INTO caregiver(user_id,full_name,status,sector,dialects)
 SELECT id,display_name,'AVAILABLE','North','English' FROM app_user
 WHERE username IN ('demo-exec-cg-a','demo-exec-cg-b');
INSERT IGNORE INTO family_member(user_id,full_name)
 SELECT id,display_name FROM app_user WHERE username='demo-exec-family';
INSERT INTO elder(full_name,sector,preferred_dialects)
 SELECT 'Execution Demo Elder','North','English'
 WHERE NOT EXISTS(SELECT 1 FROM elder WHERE full_name='Execution Demo Elder');
SET @demo_elder := (SELECT MIN(id) FROM elder WHERE full_name='Execution Demo Elder');
SET @demo_family := (SELECT f.id FROM family_member f JOIN app_user u ON u.id=f.user_id WHERE u.username='demo-exec-family');
INSERT IGNORE INTO elder_family_binding(elder_id,family_member_id,relationship,access_scope,status)
 VALUES(@demo_elder,@demo_family,'DAUGHTER','FULL','ACTIVE');
COMMIT;
