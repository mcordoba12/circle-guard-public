# stop-services.ps1 - Stop CircleGuard microservices and infrastructure
# Usage: powershell -ExecutionPolicy Bypass -File stop-services.ps1

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "================================" -ForegroundColor Green
Write-Host "CircleGuard Services Shutdown" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Green
Write-Host ""

# Step 1: Stop service windows and their processes
Write-Host "[1/3] Stopping Gradle and Java processes..." -ForegroundColor Cyan

$pidFile = "service-pids.txt"
if (Test-Path $pidFile) {
    Write-Host "Reading service PIDs from: $pidFile" -ForegroundColor Gray
    $pids = Get-Content $pidFile

    foreach ($pidEntry in $pids) {
        $parts = $pidEntry -split ":"
        if ($parts.Count -eq 2) {
            $serviceName = $parts[0]
            $parentPid = [int]$parts[1]

            Write-Host "  - Stopping $serviceName (PID: $parentPid)" -ForegroundColor Yellow

            try {
                $process = Get-Process -Id $parentPid -ErrorAction SilentlyContinue
                if ($process) {
                    # Kill parent process (cmd.exe window)
                    Stop-Process -Id $parentPid -Force -ErrorAction SilentlyContinue
                }
            }
            catch {
                Write-Host "    Warning: Could not find process with PID $parentPid" -ForegroundColor Yellow
            }
        }
    }

    # Clean up PID file
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

# Kill any remaining Java/Gradle processes
Write-Host ""
Write-Host "Cleaning up remaining processes..." -ForegroundColor Yellow
$javaProcesses = Get-Process java -ErrorAction SilentlyContinue
if ($javaProcesses) {
    Write-Host "  - Killing Java processes" -ForegroundColor Yellow
    Stop-Process -Name java -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

$gradleProcesses = Get-Process gradle -ErrorAction SilentlyContinue
if ($gradleProcesses) {
    Write-Host "  - Killing Gradle processes" -ForegroundColor Yellow
    Stop-Process -Name gradle -Force -ErrorAction SilentlyContinue
}

# Step 2: Stop Docker containers
Write-Host ""
Write-Host "[2/3] Stopping Docker containers..." -ForegroundColor Cyan

if (Test-Path "docker-compose.dev.yml") {
    docker-compose -f docker-compose.dev.yml down
    if ($LASTEXITCODE -ne 0) {
        Write-Host "WARNING: Could not stop Docker containers cleanly" -ForegroundColor Yellow
    }
} else {
    Write-Host "WARNING: docker-compose.dev.yml not found" -ForegroundColor Yellow
}

# Step 3: Verify cleanup
Write-Host ""
Write-Host "[3/3] Verifying cleanup..." -ForegroundColor Cyan

Start-Sleep -Seconds 2

$remainingJava = Get-Process java -ErrorAction SilentlyContinue
if ($remainingJava) {
    Write-Host "WARNING: $($remainingJava.Count) Java process(es) still running" -ForegroundColor Yellow
    Write-Host "Attempting force kill..." -ForegroundColor Yellow
    Stop-Process -Name java -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

Write-Host ""
Write-Host "================================" -ForegroundColor Green
Write-Host "Services stopped successfully!" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Green
Write-Host ""

# Show remaining Java processes if any
$remaining = Get-Process java -ErrorAction SilentlyContinue
if ($remaining) {
    Write-Host "Remaining Java processes:" -ForegroundColor Yellow
    $remaining | Select-Object Name, Id, Handles | Format-Table
}
else {
    Write-Host "All Java processes terminated." -ForegroundColor Green
}

Write-Host ""
Write-Host "To restart services, run: .\start-services.ps1" -ForegroundColor Gray
Write-Host ""
Read-Host "Press Enter to exit..."
