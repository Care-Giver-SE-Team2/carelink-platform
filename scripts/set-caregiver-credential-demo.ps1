# Local fictional data only. Does not start Docker or build images.
[CmdletBinding()]
param([ValidateSet('Reset','Pending','Rejected','Future','Active','Revoked')][string]$Stage = 'Reset')
$ErrorActionPreference = 'Stop'
$demoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$demoCompose = Join-Path $demoRoot 'deploy/caregiver-demo/compose.yml'
# Fixed compose project and account: no configurable production target.
$demoDockerArgs = @('compose','--project-name','carelink-caregiver-demo','-f',$demoCompose,'exec','-T','db','mysql','-ucarelink','-plocal-demo-db','carelink')
$demoCheck = "SELECT COUNT(*) FROM caregiver c JOIN app_user u ON u.id=c.user_id WHERE u.username='demo-cg-a';"
$demoCount = $demoCheck | & docker @demoDockerArgs -N -B
if ($LASTEXITCODE -ne 0 -or ($demoCount -join '').Trim() -ne '1') {
    throw 'Start the isolated caregiver demo and load slice 1 first (scripts/start-caregiver-demo.ps1).'
}
if ($Stage -eq 'Reset') {
    Get-Content -LiteralPath (Join-Path $demoRoot 'services/core/src/main/resources/db/demo/caregiver-slice2.sql') -Raw -Encoding UTF8 |
        & docker @demoDockerArgs
} else {
    $demoReadySql = "SELECT COUNT(*) FROM credential c JOIN caregiver g ON g.id=c.caregiver_id JOIN app_user u ON u.id=g.user_id JOIN credential p ON p.id=c.renews_credential_id WHERE u.username='demo-cg-a' AND c.certificate_no='DEMO2-PENDING-NEW' AND p.certificate_no='DEMO2-PENDING-OLD' AND p.caregiver_id=c.caregiver_id AND p.credential_type_id=c.credential_type_id;"
    $demoReady = $demoReadySql | & docker @demoDockerArgs -N -B
    if ($LASTEXITCODE -ne 0 -or ($demoReady -join '').Trim() -ne '1') { throw 'Run this script with -Stage Reset before changing a renewal stage.' }
    $demoStatus = switch ($Stage) { 'Pending' { 'SUBMITTED' } 'Rejected' { 'REJECTED' } 'Revoked' { 'REVOKED' } default { 'PUBLISHED' } }
    $demoStart = if ($Stage -eq 'Future') { 'DATE_ADD(CURRENT_DATE(),INTERVAL 7 DAY)' } else { 'CURRENT_DATE()' }
    # Inputs above are closed enums, never arbitrary SQL or identifiers.
    $demoSql = @"
SET time_zone = '+08:00';
UPDATE credential c JOIN caregiver g ON g.id=c.caregiver_id JOIN app_user u ON u.id=g.user_id
JOIN credential_type t ON t.id=c.credential_type_id
SET c.status='$demoStatus',c.valid_from=$demoStart,c.expiry_date=DATE_ADD(CURRENT_DATE(),INTERVAL 365 DAY)
WHERE u.username='demo-cg-a' AND t.name='Demo2 Credential Alerts' AND c.certificate_no='DEMO2-PENDING-NEW';
SELECT ROW_COUNT() AS updated_demo_rows;
"@
    $demoSql | & docker @demoDockerArgs
}
if ($LASTEXITCODE -ne 0) { throw 'Demo update failed. Inspect the database error above.' }
Write-Host "Stage: $Stage. Refresh caregiver A's schedule at http://localhost:8081/caregiver."
Write-Host 'Only DEMO2 scenario records are changed. Reset restores today-relative dates and pending renewal.'
