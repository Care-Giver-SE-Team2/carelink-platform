-- LOCAL DEMO ONLY, loaded explicitly after caregiver-slice1.sql. Never a migration.
-- Reruns reset ONLY the listed DEMO2 certificates to today's Singapore scenarios.
SET time_zone = '+08:00';
START TRANSACTION;
SET @cg := (SELECT c.id FROM caregiver c JOIN app_user u ON u.id=c.user_id WHERE u.username='demo-cg-a');
SET @today := CURRENT_DATE();
INSERT IGNORE INTO credential_type(name) VALUES ('Demo2 Credential Alerts');
SET @type := (SELECT id FROM credential_type WHERE name='Demo2 Credential Alerts');
INSERT INTO credential(caregiver_id,credential_type_id,certificate_no,expiry_date,status)
 SELECT @cg,@type,s.cert,DATE_ADD(@today,INTERVAL 365 DAY),'PUBLISHED'
 FROM (
   SELECT 'DEMO2-EXPIRED' AS cert UNION ALL SELECT 'DEMO2-TODAY'
   UNION ALL SELECT 'DEMO2-BOUNDARY30' UNION ALL SELECT 'DEMO2-OUTSIDE31'
   UNION ALL SELECT 'DEMO2-PERMANENT'
   UNION ALL SELECT 'DEMO2-PENDING-OLD' UNION ALL SELECT 'DEMO2-PENDING-NEW'
   UNION ALL SELECT 'DEMO2-FUTURE-OLD' UNION ALL SELECT 'DEMO2-FUTURE-NEW'
   UNION ALL SELECT 'DEMO2-ACTIVE-OLD' UNION ALL SELECT 'DEMO2-ACTIVE-NEW'
 ) s
 WHERE @cg IS NOT NULL AND NOT EXISTS (
   SELECT 1 FROM credential c WHERE c.caregiver_id=@cg AND c.certificate_no=s.cert);
UPDATE credential SET valid_from=NULL,renews_credential_id=NULL,status='PUBLISHED',
 expiry_date=CASE certificate_no
   WHEN 'DEMO2-EXPIRED' THEN DATE_SUB(@today,INTERVAL 1 DAY)
   WHEN 'DEMO2-TODAY' THEN @today
   WHEN 'DEMO2-BOUNDARY30' THEN DATE_ADD(@today,INTERVAL 30 DAY)
   WHEN 'DEMO2-OUTSIDE31' THEN DATE_ADD(@today,INTERVAL 31 DAY)
   WHEN 'DEMO2-PERMANENT' THEN '9999-12-31'
   WHEN 'DEMO2-PENDING-OLD' THEN DATE_SUB(@today,INTERVAL 1 DAY)
   WHEN 'DEMO2-FUTURE-OLD' THEN DATE_SUB(@today,INTERVAL 1 DAY)
   WHEN 'DEMO2-ACTIVE-OLD' THEN DATE_SUB(@today,INTERVAL 1 DAY)
   ELSE DATE_ADD(@today,INTERVAL 365 DAY) END
 WHERE caregiver_id=@cg AND credential_type_id=@type AND certificate_no IN (
 'DEMO2-EXPIRED','DEMO2-TODAY','DEMO2-BOUNDARY30','DEMO2-OUTSIDE31','DEMO2-PERMANENT',
 'DEMO2-PENDING-OLD','DEMO2-PENDING-NEW','DEMO2-FUTURE-OLD','DEMO2-FUTURE-NEW','DEMO2-ACTIVE-OLD','DEMO2-ACTIVE-NEW');
SET @pending := (SELECT id FROM credential WHERE caregiver_id=@cg AND certificate_no='DEMO2-PENDING-OLD');
SET @future := (SELECT id FROM credential WHERE caregiver_id=@cg AND certificate_no='DEMO2-FUTURE-OLD');
SET @active := (SELECT id FROM credential WHERE caregiver_id=@cg AND certificate_no='DEMO2-ACTIVE-OLD');
UPDATE credential SET renews_credential_id=@pending,status='SUBMITTED'
 WHERE caregiver_id=@cg AND credential_type_id=@type AND certificate_no='DEMO2-PENDING-NEW';
UPDATE credential SET renews_credential_id=@future,valid_from=DATE_ADD(@today,INTERVAL 7 DAY)
 WHERE caregiver_id=@cg AND credential_type_id=@type AND certificate_no='DEMO2-FUTURE-NEW';
UPDATE credential SET renews_credential_id=@active,valid_from=@today
 WHERE caregiver_id=@cg AND credential_type_id=@type AND certificate_no='DEMO2-ACTIVE-NEW';
COMMIT;
SELECT @today AS assessment_date,@cg AS demo_caregiver_id,'Expect five DEMO2 reminders with the default 30-day window' AS expected;
