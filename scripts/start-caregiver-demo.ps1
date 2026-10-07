# Run from any directory. Requires Docker Desktop running Linux containers.
[CmdletBinding()]
param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$demoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$demoCompose = Join-Path $demoRoot 'deploy/caregiver-demo/compose.yml'
$demoSeed = Join-Path $demoRoot 'services/core/src/main/resources/db/demo/caregiver-slice1.sql'
& docker info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop (Linux containers), then try again.' }
$demoArgs = @('compose', '--project-name', 'carelink-caregiver-demo', '-f', $demoCompose, 'up', '-d', '--wait', '--wait-timeout', '240')
if (-not $SkipBuild) { $demoArgs += '--build' }
& docker @demoArgs
if ($LASTEXITCODE -ne 0) { throw 'Demo startup failed. Inspect docker compose logs for this demo project.' }
# Read SQL through stdin: never interpolate it into a shell command.
Get-Content -LiteralPath $demoSeed -Raw -Encoding UTF8 | & docker compose --project-name carelink-caregiver-demo -f $demoCompose exec -T db mysql -ucarelink -plocal-demo-db carelink
if ($LASTEXITCODE -ne 0) { throw 'Demo data load failed. It is safe to rerun after resolving the error.' }
Write-Host 'Ready: http://localhost:8081'
Write-Host 'Caregiver A: demo-cg-a   Caregiver B: demo-cg-b'
Write-Host 'Password (both, LOCAL DEMO ONLY): Demo#2026'
Write-Host 'Use the demo_date and visit ids printed above. Reruns keep the original date and data.'
Write-Host 'Guide: docs/caregiver/slice-1-demo.md'
