-- LOCAL DEMO ONLY. Explicitly loaded after Flyway; never a migration or startup seed.
-- Password for both fictional caregivers: Demo#2026 (bcrypt below).
-- One connection, one transaction. No fixed ids; reruns preserve the original demo date.
-- JVM, JDBC and MySQL must agree on Asia/Singapore (see demo compose).
START TRANSACTION;
INSERT IGNORE INTO app_user (username,password_hash,display_name) VALUES
 ('demo-cg-a','{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm','Demo Caregiver A'),
 ('demo-cg-b','{bcrypt}$2b$10$8hmBV1GponkcfLJprTKf8.glEBN2.HpU3ZGuO7JTvDMZem5qTm9Tm','Demo Caregiver B');
SET @ua := (SELECT id FROM app_user WHERE username='demo-cg-a');
SET @ub := (SELECT id FROM app_user WHERE username='demo-cg-b');
INSERT IGNORE INTO user_role (user_id,role) VALUES (@ua,'CAREGIVER'),(@ub,'CAREGIVER');
INSERT IGNORE INTO caregiver (user_id,full_name,sector,dialects,status) VALUES
 (@ua,'Demo Caregiver A','North','English,Mandarin','AVAILABLE'),
 (@ub,'Demo Caregiver B','North','English','AVAILABLE');
SET @ca := (SELECT id FROM caregiver WHERE user_id=@ua);
SET @cb := (SELECT id FROM caregiver WHERE user_id=@ub);
INSERT INTO elder (full_name,address,postal_code,sector,preferred_dialects,medical_notes)
 SELECT 'Demo Elder Mei','10 Demo Street, Unit 02-01 (fictional)','760010','North','English,Mandarin','PRIVATE-DEMO-NOTE-MUST-NOT-BE-EXPOSED'
 WHERE NOT EXISTS (SELECT 1 FROM elder WHERE full_name='Demo Elder Mei');
SET @e := (SELECT MIN(id) FROM elder WHERE full_name='Demo Elder Mei');
SET @day := COALESCE((SELECT DATE(MIN(scheduled_start)) FROM visit WHERE elder_id=@e AND service_type='DEMO_MORNING_CARE'), CURRENT_DATE());
INSERT INTO care_plan (elder_id,version,status,published_at,start_date)
 SELECT @e,1,'SUPERSEDED',NOW(),@day WHERE NOT EXISTS (SELECT 1 FROM care_plan WHERE elder_id=@e AND version=1);
SET @p1 := (SELECT MIN(id) FROM care_plan WHERE elder_id=@e AND version=1);
INSERT INTO care_plan (elder_id,version,status,supersedes_plan_id,published_at,start_date)
 SELECT @e,2,'PUBLISHED',@p1,NOW(),@day WHERE NOT EXISTS (SELECT 1 FROM care_plan WHERE elder_id=@e AND version=2);
SET @p2 := (SELECT MIN(id) FROM care_plan WHERE elder_id=@e AND version=2);
INSERT INTO care_plan_node (care_plan_id,name,evidence_type,display_order)
 SELECT @p1,'Assist with morning hygiene','CHECKLIST',1 WHERE NOT EXISTS (SELECT 1 FROM care_plan_node WHERE care_plan_id=@p1 AND display_order=1);
INSERT INTO care_plan_node (care_plan_id,name,evidence_type,display_order)
 SELECT @p1,'Record blood pressure','READING',2 WHERE NOT EXISTS (SELECT 1 FROM care_plan_node WHERE care_plan_id=@p1 AND display_order=2);
INSERT INTO care_plan_node (care_plan_id,name,evidence_type,display_order)
 SELECT @p1,'Friendly conversation','NONE',3 WHERE NOT EXISTS (SELECT 1 FROM care_plan_node WHERE care_plan_id=@p1 AND display_order=3);
INSERT INTO care_plan_node (care_plan_id,name,evidence_type,display_order)
 SELECT @p1,'Unassigned photo task','PHOTO',4 WHERE NOT EXISTS (SELECT 1 FROM care_plan_node WHERE care_plan_id=@p1 AND display_order=4);
INSERT INTO care_plan_node (care_plan_id,name,evidence_type,display_order)
 SELECT @p2,'Version 2 task - must not appear in v1 visit','PHOTO',1 WHERE NOT EXISTS (SELECT 1 FROM care_plan_node WHERE care_plan_id=@p2 AND display_order=1);
SET @n1 := (SELECT MIN(id) FROM care_plan_node WHERE care_plan_id=@p1 AND display_order=1);
SET @n2 := (SELECT MIN(id) FROM care_plan_node WHERE care_plan_id=@p1 AND display_order=2);
SET @n3 := (SELECT MIN(id) FROM care_plan_node WHERE care_plan_id=@p1 AND display_order=3);
INSERT INTO visit (elder_id,caregiver_id,care_plan_id,care_plan_node_id,service_type,scheduled_start,scheduled_end)
 SELECT @e,@ca,@p1,@n1,'DEMO_MORNING_CARE',TIMESTAMP(@day,'09:00:00'),TIMESTAMP(@day,'10:00:00')
 WHERE NOT EXISTS (SELECT 1 FROM visit WHERE elder_id=@e AND service_type='DEMO_MORNING_CARE');
INSERT INTO visit (elder_id,caregiver_id,care_plan_id,care_plan_node_id,service_type,scheduled_start,scheduled_end)
 SELECT @e,@ca,@p1,@n3,'DEMO_COMPANIONSHIP',TIMESTAMP(@day,'14:00:00'),TIMESTAMP(@day,'15:00:00')
 WHERE NOT EXISTS (SELECT 1 FROM visit WHERE elder_id=@e AND service_type='DEMO_COMPANIONSHIP');
INSERT INTO visit (elder_id,caregiver_id,care_plan_id,care_plan_node_id,service_type,scheduled_start,scheduled_end)
 SELECT @e,@cb,@p1,@n1,'DEMO_REASSIGNED_CARE',TIMESTAMP(@day,'16:00:00'),TIMESTAMP(@day,'17:00:00')
 WHERE NOT EXISTS (SELECT 1 FROM visit WHERE elder_id=@e AND service_type='DEMO_REASSIGNED_CARE');
SET @va := (SELECT MIN(id) FROM visit WHERE elder_id=@e AND service_type='DEMO_MORNING_CARE');
SET @va2 := (SELECT MIN(id) FROM visit WHERE elder_id=@e AND service_type='DEMO_COMPANIONSHIP');
SET @vb := (SELECT MIN(id) FROM visit WHERE elder_id=@e AND service_type='DEMO_REASSIGNED_CARE');
INSERT INTO visit_task (visit_id,care_plan_node_id,name)
 SELECT @va,id,name FROM care_plan_node n WHERE id IN (@n1,@n2,@n3)
 AND NOT EXISTS (SELECT 1 FROM visit_task t WHERE t.visit_id=@va AND t.care_plan_node_id=n.id);
INSERT INTO visit_task (visit_id,care_plan_node_id,name)
 SELECT @va2,id,name FROM care_plan_node n WHERE id=@n3
 AND NOT EXISTS (SELECT 1 FROM visit_task t WHERE t.visit_id=@va2 AND t.care_plan_node_id=n.id);
INSERT INTO visit_task (visit_id,care_plan_node_id,name)
 SELECT @vb,id,name FROM care_plan_node n WHERE id=@n1
 AND NOT EXISTS (SELECT 1 FROM visit_task t WHERE t.visit_id=@vb AND t.care_plan_node_id=n.id);
INSERT INTO visit_assignment (visit_id,caregiver_id,status,reason,ended_at)
 SELECT @vb,@ca,'REPLACED','Demo: reassigned from A to B',NOW()
 WHERE NOT EXISTS (SELECT 1 FROM visit_assignment WHERE visit_id=@vb AND caregiver_id=@ca);
INSERT INTO visit_assignment (visit_id,caregiver_id,status,reason)
 SELECT @vb,@cb,'ACTIVE','Demo: current assignee'
 WHERE NOT EXISTS (SELECT 1 FROM visit_assignment WHERE visit_id=@vb AND caregiver_id=@cb);
INSERT IGNORE INTO credential_type (name) VALUES ('Demo First Aid'),('Demo Care Skills');
SET @ct1 := (SELECT id FROM credential_type WHERE name='Demo First Aid');
SET @ct2 := (SELECT id FROM credential_type WHERE name='Demo Care Skills');
INSERT INTO credential (caregiver_id,credential_type_id,certificate_no,expiry_date,status)
 SELECT @ca,@ct1,'DEMO-EXPIRED',DATE_SUB(@day,INTERVAL 1 DAY),'EXPIRED'
 WHERE NOT EXISTS (SELECT 1 FROM credential WHERE caregiver_id=@ca AND certificate_no='DEMO-EXPIRED');
INSERT INTO credential (caregiver_id,credential_type_id,certificate_no,expiry_date,status)
 SELECT @ca,@ct2,'DEMO-EXPIRING',DATE_ADD(@day,INTERVAL 14 DAY),'PUBLISHED'
 WHERE NOT EXISTS (SELECT 1 FROM credential WHERE caregiver_id=@ca AND certificate_no='DEMO-EXPIRING');
COMMIT;
SELECT @day AS demo_date,@va AS caregiver_a_morning_visit,@va2 AS caregiver_a_afternoon_visit,@vb AS caregiver_b_reassigned_visit,DATE_ADD(@day,INTERVAL 7 DAY) AS empty_date;
