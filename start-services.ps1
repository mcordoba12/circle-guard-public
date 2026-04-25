# start-services.ps1 - Start CircleGuard microservices with better process management
# Usage: powershell -ExecutionPolicy Bypass -File start-services.ps1

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "================================" -ForegroundColor Green
Write-Host "CircleGuard Services Startup" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Green
Write-Host ""

# Check prerequisites
if (-not (Test-Path "docker-compose.dev.yml")) {
    Write-Host "ERROR: docker-compose.dev.yml not found" -ForegroundColor Red
    Write-Host "Run this script from the root of the circle-guard-public project"
    exit 1
}

if (-not (Test-Path "gradlew.bat")) {
    Write-Host "ERROR: gradlew.bat not found" -ForegroundColor Red
    exit 1
}

# Create logs directory
if (-not (Test-Path "logs")) {
    New-Item -ItemType Directory -Name "logs" | Out-Null
}

# Create PID tracking file
$pidFile = "service-pids.txt"
if (Test-Path $pidFile) {
    Remove-Item $pidFile -Force
}

# Step 1: Start Docker infrastructure
Write-Host "[1/3] Starting Docker infrastructure..." -ForegroundColor Cyan
docker-compose -f docker-compose.dev.yml up -d

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Failed to start Docker infrastructure" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Waiting 15 seconds for containers to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# Check for existing Java processes
Write-Host ""
Write-Host "[2/3] Checking for existing services..." -ForegroundColor Cyan
$existingJava = Get-Process java -ErrorAction SilentlyContinue
if ($existingJava) {
    Write-Host "WARNING: Found $($existingJava.Count) existing Java process(es)" -ForegroundColor Yellow
    Write-Host "If you encounter port conflicts, run: stop-services.ps1" -ForegroundColor Yellow
    Write-Host "Waiting 5 seconds..." -ForegroundColor Yellow
    Start-Sleep -Seconds 5
}

# Step 3: Start services
Write-Host ""
Write-Host "[3/3] Starting CircleGuard microservices..." -ForegroundColor Cyan
Write-Host ""
Write-Host "Services will open in separate windows with logs displayed in real-time" -ForegroundColor Yellow
Write-Host ""

$services = @(
    "circleguard-auth-service",
    "circleguard-identity-service",
    "circleguard-form-service",
    "circleguard-promotion-service",
    "circleguard-notification-service",
    "circleguard-gateway-service"
)

$ports = @{
    "circleguard-auth-service" = "8180"
    "circleguard-identity-service" = "8085"
    "circleguard-form-service" = "8086"
    "circleguard-gateway-service" = "8087"
    "circleguard-promotion-service" = "8088"
    "circleguard-notification-service" = "8089"
}

foreach ($service in $services) {
    $port = $ports[$service]
    Write-Host "[STARTING] $service (port $port)" -ForegroundColor Yellow

    # Start service in new window and capture PID
    $process = Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/k title CircleGuard - $service && call ./gradlew :services:${service}:bootRun" `
        -WindowStyle Normal `
        -PassThru

    # Store PID for later reference
    "$service`:$($process.Id)" | Add-Content -Path $pidFile

    # Small delay between service starts to avoid overwhelming the system
    Start-Sleep -Seconds 2
}

Write-Host ""
Write-Host "================================" -ForegroundColor Green
Write-Host "Startup sequence complete!" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Green
Write-Host ""

Write-Host "Services will be available at:" -ForegroundColor Cyan
Write-Host "  - Auth Service:         http://localhost:8180"
Write-Host "  - Identity Service:     http://localhost:8085"
Write-Host "  - Form Service:         http://localhost:8086"
Write-Host "  - Gateway Service:      http://localhost:8087"
Write-Host "  - Promotion Service:    http://localhost:8088"
Write-Host "  - Notification Service: http://localhost:8089"
Write-Host ""

Write-Host "Each service has opened in its own window." -ForegroundColor Yellow
Write-Host "Monitor them for startup completion (typically 30-60 seconds)" -ForegroundColor Yellow
Write-Host ""

Write-Host "Service PIDs stored in: $pidFile" -ForegroundColor Gray
Write-Host "To stop all services: .\stop-services.ps1" -ForegroundColor Gray
Write-Host "To view logs: Get-Content logs\<service-name>.log -Tail 50" -ForegroundColor Gray
Write-Host ""

Write-Host "Press Enter to continue..." -ForegroundColor Cyan
Read-Host
