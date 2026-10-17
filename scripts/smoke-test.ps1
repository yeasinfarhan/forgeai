<#
.SYNOPSIS
  Phase 1 smoke test for ForgeAI on Windows.

.DESCRIPTION
  Checks the toolchain, builds and tests the backend, builds the frontend, starts PostgreSQL,
  runs the backend against it and calls GET /api/health. Finally builds and starts the full
  Docker Compose stack and checks it end to end.

  Steps whose tools are missing are reported as SKIPPED rather than failing the run.
  Output is written to scripts/smoke-test.log.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
#>
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$log = Join-Path $PSScriptRoot 'smoke-test.log'
Set-Location $root
Start-Transcript -Path $log -Force | Out-Null

$results = [ordered]@{}
function Has($cmd) { [bool](Get-Command $cmd -ErrorAction SilentlyContinue) }
function Section($name) { Write-Host "`n==================== $name ====================" }
function Wait-Http($url, $seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try { return Invoke-RestMethod -Uri $url -TimeoutSec 5 } catch { Start-Sleep -Seconds 3 }
    }
    return $null
}

Section 'Toolchain'
foreach ($tool in 'java', 'mvn', 'node', 'npm', 'docker') {
    if (Has $tool) {
        $version = switch ($tool) {
            'java'   { (& java -version 2>&1 | Select-Object -First 1) }
            'mvn'    { (& mvn -v 2>&1 | Select-Object -First 1) }
            'docker' { (& docker version --format '{{.Server.Version}}' 2>&1) }
            default  { (& $tool -v 2>&1) }
        }
        Write-Host ("{0,-7} {1}" -f $tool, $version)
    } else {
        Write-Host ("{0,-7} NOT INSTALLED" -f $tool)
    }
}
$dockerReady = (Has 'docker') -and ((& docker info 2>&1 | Out-String) -notmatch 'error during connect|Cannot connect')
Write-Host "Docker engine running: $dockerReady"

Section 'Environment file'
if (-not (Test-Path '.env')) {
    $password = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
    (Get-Content '.env.example') -replace '^DATABASE_PASSWORD=.*', "DATABASE_PASSWORD=$password" | Set-Content '.env'
    Write-Host 'Created .env from .env.example with a generated development database password.'
} else {
    Write-Host '.env already exists; using it.'
}

Section 'Backend: mvn test + package'
if ((Has 'mvn') -and (Has 'java')) {
    Push-Location backend
    & mvn -B test package 2>&1 | Select-Object -Last 40
    $results['Backend build + tests'] = if ($LASTEXITCODE -eq 0) { 'PASS' } else { 'FAIL' }
    Pop-Location
} else { $results['Backend build + tests'] = 'SKIPPED (java/mvn not installed)' }

Section 'Frontend: npm install + build'
if (Has 'npm') {
    Push-Location frontend
    & npm install --no-audit --no-fund 2>&1 | Select-Object -Last 15
    & npm run build 2>&1 | Select-Object -Last 25
    $results['Frontend build'] = if ($LASTEXITCODE -eq 0) { 'PASS' } else { 'FAIL' }
    Pop-Location
} else { $results['Frontend build'] = 'SKIPPED (node/npm not installed)' }

Section 'PostgreSQL container'
if ($dockerReady) {
    & docker compose up -d postgres 2>&1
    $healthy = $false
    for ($i = 0; $i -lt 30 -and -not $healthy; $i++) {
        Start-Sleep -Seconds 2
        $healthy = (& docker inspect --format '{{.State.Health.Status}}' forgeai-postgres-1 2>$null) -eq 'healthy'
    }
    $results['PostgreSQL healthy'] = if ($healthy) { 'PASS' } else { 'FAIL' }
} else { $results['PostgreSQL healthy'] = 'SKIPPED (Docker not running)' }

Section 'Backend (local jar) against PostgreSQL'
$jar = Get-ChildItem backend\target\forgeai-backend-*.jar -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jar -and $results['PostgreSQL healthy'] -eq 'PASS') {
    $backendLog = Join-Path $PSScriptRoot 'smoke-test-backend.log'
    $proc = Start-Process java -ArgumentList '-jar', $jar.FullName -WorkingDirectory (Join-Path $root 'backend') `
        -RedirectStandardOutput $backendLog -RedirectStandardError "$backendLog.err" -PassThru -WindowStyle Hidden
    $health = Wait-Http 'http://localhost:8080/api/health' 90
    Write-Host "Response: $($health | ConvertTo-Json -Compress)"
    $connected = Select-String -Path $backendLog -Pattern 'Connected to PostgreSQL' -Quiet
    Select-String -Path $backendLog -Pattern 'Connected to|Started ForgeAiApplication|ERROR' | ForEach-Object { $_.Line }
    try { Invoke-WebRequest 'http://localhost:8080/api/projects' -UseBasicParsing -ErrorAction Stop | Out-Null; $secured = $false }
    catch { $secured = $_.Exception.Response.StatusCode.value__ -eq 401 }
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    $results['Local backend /api/health'] = if ($health.status -eq 'UP' -and $health.service -eq 'ForgeAI') { 'PASS' } else { 'FAIL' }
    $results['Local backend DB connection'] = if ($connected) { 'PASS' } else { 'FAIL' }
    $results['Unknown endpoint -> 401'] = if ($secured) { 'PASS' } else { 'FAIL' }
} else { $results['Local backend /api/health'] = 'SKIPPED (no backend jar or no database)' }

Section 'Full Docker Compose stack'
if ($dockerReady) {
    & docker compose up --build -d 2>&1 | Select-Object -Last 30
    $apiHealth = Wait-Http 'http://localhost:8080/api/health' 180
    $proxiedHealth = Wait-Http 'http://localhost:3000/api/health' 60
    $page = try { (Invoke-WebRequest 'http://localhost:3000/' -UseBasicParsing).Content } catch { '' }
    Write-Host "Backend container: $($apiHealth | ConvertTo-Json -Compress)"
    Write-Host "Via frontend nginx: $($proxiedHealth | ConvertTo-Json -Compress)"
    & docker compose ps 2>&1
    & docker compose logs backend 2>&1 | Select-String 'Connected to|Started ForgeAiApplication|ERROR' | ForEach-Object { $_.Line }
    $results['Compose backend /api/health'] = if ($apiHealth.status -eq 'UP') { 'PASS' } else { 'FAIL' }
    $results['Compose frontend proxy /api/health'] = if ($proxiedHealth.status -eq 'UP') { 'PASS' } else { 'FAIL' }
    $results['Compose frontend serves app'] = if ($page -match '<div id="root">') { 'PASS' } else { 'FAIL' }
} else { $results['Docker Compose stack'] = 'SKIPPED (Docker not running)' }

Section 'Summary'
$results.GetEnumerator() | ForEach-Object { Write-Host ("{0,-38} {1}" -f $_.Key, $_.Value) }
Write-Host "`nThe Compose stack is left running: frontend http://localhost:3000, backend http://localhost:8080"
Write-Host 'Stop it with: docker compose down'
Write-Host 'SMOKE TEST FINISHED'
Stop-Transcript | Out-Null
