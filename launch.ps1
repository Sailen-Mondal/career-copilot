# launch.ps1
# Script to launch the AI Career Copilot development environment

$PSScriptRoot = Split-Path -Parent -Path $MyInvocation.MyCommand.Definition

# Load .env variables into process scope if .env exists
if (Test-Path "$PSScriptRoot/.env") {
    Write-Host "Loading environment variables from .env..." -ForegroundColor Cyan
    Get-Content "$PSScriptRoot/.env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line -match "^([^=]+)=(.*)$") {
            $key = $Matches[1].Trim()
            $val = $Matches[2].Trim()
            [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
            Set-Item "env:$key" $val
        }
    }
}

# 1. Start Docker Desktop if not running
Write-Host "Checking if Docker daemon is running..." -ForegroundColor Cyan
try {
    docker ps > $null 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Docker is not running. Starting Docker Desktop..." -ForegroundColor Yellow
        Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
        Write-Host "Waiting for Docker daemon to become responsive..." -ForegroundColor Yellow
        while ($true) {
            Start-Sleep -Seconds 5
            docker ps > $null 2>&1
            if ($LASTEXITCODE -eq 0) {
                break
            }
            Write-Host "Still waiting for Docker..." -ForegroundColor Gray
        }
    }
} catch {
    Write-Host "Failed to verify or start Docker: $_" -ForegroundColor Red
}

# 2. Spin up Docker containers (Postgres & Redis)
Write-Host "`nSpinning up Postgres and Redis..." -ForegroundColor Cyan
Set-Location "$PSScriptRoot/infra"
docker compose up -d
Set-Location $PSScriptRoot

# 3. Wait for ports 5432 and 6379 to accept connections
Write-Host "`nWaiting for database and cache ports..." -ForegroundColor Cyan
while ($true) {
    $pg = Test-NetConnection -ComputerName localhost -Port 5432 -WarningAction SilentlyContinue
    $redis = Test-NetConnection -ComputerName localhost -Port 6379 -WarningAction SilentlyContinue
    if ($pg.TcpTestSucceeded -and $redis.TcpTestSucceeded) {
        Write-Host "Ports are open and ready!" -ForegroundColor Green
        break
    }
    Write-Host "Waiting for database and cache services..." -ForegroundColor Gray
    Start-Sleep -Seconds 2
}

# 4. Start Spring Boot Backend in a new window
Write-Host "`nStarting Spring Boot Backend..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot'; Write-Host 'Booting up Spring Boot Backend...'; ./gradlew bootRun"

# 5. Start Automation Worker in a new window
Write-Host "`nStarting Playwright Automation Worker..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot/automation'; Write-Host 'Booting up Automation Worker...'; npm start"

# 6. Wait for backend to be healthy, then launch dashboard
Write-Host "`nWaiting for Spring Boot backend to become healthy on port 8080..." -ForegroundColor Cyan
while ($true) {
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get -ErrorAction Stop
        if ($health.status -eq "UP") {
            Write-Host "Backend is healthy!" -ForegroundColor Green
            break
        }
    } catch {
        # ignore connection errors
    }
    Start-Sleep -Seconds 2
}

# 7. Open the dashboard
Write-Host "`nOpening Dashboard..." -ForegroundColor Cyan
Start-Process "$PSScriptRoot/frontend/index.html"

Write-Host "`nAll services launched successfully!" -ForegroundColor Green
