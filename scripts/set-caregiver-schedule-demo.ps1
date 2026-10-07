# Explicitly approved LOCAL fictional demo only. No production endpoint or arbitrary IDs.
[CmdletBinding()]
param([ValidateSet('Reset','Reschedule','NextDay','Reassign','Unassign','Cancel')][string]$Stage='Reset')
$ErrorActionPreference='Stop'
$demoRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$demoCompose=Join-Path $demoRoot 'deploy/caregiver-demo/compose.yml'
$demoArgs=@('compose','--project-name','carelink-caregiver-demo','-f',$demoCompose,'exec','-T','db','mysql','-ucarelink','-plocal-demo-db','carelink')
$demoCount="SELECT COUNT(*) FROM caregiver c JOIN app_user u ON u.id=c.user_id WHERE u.username IN ('demo-cg-a','demo-cg-b');" | & docker @demoArgs -N -B
if ($LASTEXITCODE -ne 0 -or ($demoCount -join '').Trim() -ne '2') { throw 'Start scripts/start-caregiver-demo.ps1 before loading schedule scenarios.' }
if ($Stage -eq 'Reset') {
    Get-Content -LiteralPath (Join-Path $demoRoot 'services/core/src/main/resources/db/demo/caregiver-slice3.sql') -Raw -Encoding UTF8 | & docker @demoArgs
    if ($LASTEXITCODE -ne 0) { throw 'Demo seed failed; no stage update attempted.' }
}
$demoReady="SELECT COUNT(*) FROM visit v JOIN elder e ON e.id=v.elder_id WHERE e.full_name='Demo3 Elder Lin' AND v.service_type IN ('DEMO3_CHANGE','DEMO3_CONTROL');" | & docker @demoArgs -N -B
if ($LASTEXITCODE -ne 0 -or ($demoReady -join '').Trim() -ne '2') { throw 'Run -Stage Reset first; expected exactly the two named fictional visits.' }
# Closed enum expressions; no user-supplied SQL, IDs or database targets.
$demoOwner=switch ($Stage) { 'Reset' {'@ca'} 'Reassign' {'@cb'} 'Unassign' {'NULL'} default {'@oldOwner'} }
$demoStatus=if ($Stage -eq 'Cancel') { "'CANCELLED'" } elseif ($Stage -eq 'Reset') { "'SCHEDULED'" } else { '@oldStatus' }
$demoStart=switch ($Stage) { 'Reset' {"TIMESTAMP(CURRENT_DATE(),'09:00:00')"} 'Reschedule' {"TIMESTAMP(CURRENT_DATE(),'11:00:00')"} 'NextDay' {"TIMESTAMP(DATE_ADD(CURRENT_DATE(),INTERVAL 1 DAY),'09:00:00')"} default {'@oldStart'} }
$demoSql=@"
SET time_zone = '+08:00';
START TRANSACTION;
SET @ca := (SELECT c.id FROM caregiver c JOIN app_user u ON u.id=c.user_id WHERE u.username='demo-cg-a');
SET @cb := (SELECT c.id FROM caregiver c JOIN app_user u ON u.id=c.user_id WHERE u.username='demo-cg-b');
SET @v := (SELECT v.id FROM visit v JOIN elder e ON e.id=v.elder_id WHERE e.full_name='Demo3 Elder Lin' AND v.service_type='DEMO3_CHANGE');
SELECT caregiver_id,status,scheduled_start INTO @oldOwner,@oldStatus,@oldStart FROM visit WHERE id=@v FOR UPDATE;
SET @nextOwner := $demoOwner;
SET @nextStatus := $demoStatus;
SET @nextStart := $demoStart;
UPDATE visit_assignment SET status=IF(@nextStatus='CANCELLED' OR @nextOwner IS NULL,'CANCELLED','REPLACED'),ended_at=NOW()
 WHERE visit_id=@v AND status='ACTIVE' AND (NOT(caregiver_id <=> @nextOwner) OR @nextStatus='CANCELLED');
UPDATE visit SET caregiver_id=@nextOwner,status=@nextStatus,scheduled_start=@nextStart,scheduled_end=DATE_ADD(@nextStart,INTERVAL 1 HOUR),version=version+1 WHERE id=@v;
INSERT INTO visit_assignment(visit_id,caregiver_id,status,reason)
 SELECT @v,@nextOwner,'ACTIVE','DEMO3 explicit local scenario'
 WHERE @nextOwner IS NOT NULL AND @nextStatus<>'CANCELLED' AND NOT EXISTS (SELECT 1 FROM visit_assignment WHERE visit_id=@v AND status='ACTIVE');
"@
if ($Stage -eq 'Reset') {
    $demoSql += @"

UPDATE visit v JOIN elder e ON e.id=v.elder_id SET v.scheduled_start=TIMESTAMP(CURRENT_DATE(),'10:00:00'),v.scheduled_end=TIMESTAMP(CURRENT_DATE(),'11:00:00')
 WHERE e.full_name='Demo3 Elder Lin' AND v.service_type='DEMO3_CONTROL';
"@
}
$demoSql += @"

COMMIT;
SELECT id AS changed_visit,caregiver_id,status,scheduled_start,scheduled_end FROM visit WHERE id=@v;
SELECT CURRENT_DATE() AS demo_date,DATE_ADD(CURRENT_DATE(),INTERVAL 1 DAY) AS next_date;
"@
$demoSql | & docker @demoArgs
if ($LASTEXITCODE -ne 0) { throw 'Demo stage failed. Review the error; transaction rolls back on connection closure if uncommitted.' }
Write-Host "Stage $Stage applied to DEMO3_CHANGE only (Reset also refreshes the control visit date)."
Write-Host 'Refresh or return to http://localhost:8081/caregiver. This script is a test fixture, not a manager workflow.'
