-- LOCAL ONLY: explicit seed, never Flyway. Requires slice 1 accounts.
-- Own fictional elder, plan and visits. No fixed ids or deletion of earlier scenarios.
SET time_zone = '+08:00';
START TRANSACTION;
SET @ca := (SELECT c.id FROM caregiver c JOIN app_user u ON u.id=c.user_id WHERE u.username='demo-cg-a');
INSERT INTO elder(full_name,address,sector,preferred_dialects)
 SELECT 'Demo3 Elder Lin','30 Fictional Demo Road','North','English'
 WHERE NOT EXISTS (SELECT 1 FROM elder WHERE full_name='Demo3 Elder Lin');
SET @elder := (SELECT MIN(id) FROM elder WHERE full_name='Demo3 Elder Lin');
INSERT INTO care_plan(elder_id,version,status,start_date,published_at)
 SELECT @elder,1,'SUPERSEDED',CURRENT_DATE(),NOW() WHERE NOT EXISTS (SELECT 1 FROM care_plan WHERE elder_id=@elder AND version=1);
SET @plan := (SELECT id FROM care_plan WHERE elder_id=@elder AND version=1);
INSERT INTO care_plan(elder_id,version,status,start_date,supersedes_plan_id,published_at)
 SELECT @elder,2,'PUBLISHED',CURRENT_DATE(),@plan,NOW() WHERE NOT EXISTS (SELECT 1 FROM care_plan WHERE elder_id=@elder AND version=2);
INSERT INTO care_plan_node(care_plan_id,name,evidence_type,display_order)
 SELECT @plan,'Demo3 assigned version 1 checklist','CHECKLIST',1
 WHERE NOT EXISTS (SELECT 1 FROM care_plan_node WHERE care_plan_id=@plan AND display_order=1);
SET @node := (SELECT id FROM care_plan_node WHERE care_plan_id=@plan AND display_order=1);
INSERT INTO visit(elder_id,caregiver_id,care_plan_id,care_plan_node_id,service_type,scheduled_start,scheduled_end)
 SELECT @elder,@ca,@plan,@node,s.kind,TIMESTAMP(CURRENT_DATE(),s.start_time),TIMESTAMP(CURRENT_DATE(),s.end_time)
 FROM (SELECT 'DEMO3_CHANGE' kind,'09:00:00' start_time,'10:00:00' end_time UNION ALL SELECT 'DEMO3_CONTROL','10:00:00','11:00:00') s
 WHERE NOT EXISTS (SELECT 1 FROM visit v WHERE v.elder_id=@elder AND v.service_type=s.kind);
INSERT INTO visit_task(visit_id,care_plan_node_id,name)
 SELECT v.id,@node,'Demo3 assigned version 1 checklist' FROM visit v
 WHERE v.elder_id=@elder AND v.service_type IN ('DEMO3_CHANGE','DEMO3_CONTROL')
 AND NOT EXISTS (SELECT 1 FROM visit_task t WHERE t.visit_id=v.id AND t.care_plan_node_id=@node);
COMMIT;
