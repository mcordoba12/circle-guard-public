@echo off
REM start-services.bat - Start CircleGuard microservices
REM Improved version with better process management and visibility

setlocal enabledelayedexpansion

echo.
echo ================================
echo CircleGuard Services Startup
echo ================================
echo.

REM Check if docker-compose is available
docker-compose --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: docker-compose not found or not installed
    echo Please install Docker Desktop first
    pause
    exit /b 1
)

REM Check if gradlew exists
if not exist "gradlew.bat" (
    echo ERROR: gradlew.bat not found
    echo Run this script from the root of the circle-guard-public project
    pause
    exit /b 1
)

REM Step 1: Start Docker infrastructure
echo [1/3] Starting Docker infrastructure...
docker-compose -f docker-compose.dev.yml up -d

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to start Docker infrastructure
    pause
    exit /b 1
)

echo.
echo Waiting 15 seconds for containers to be ready...
timeout /t 15 /nobreak

echo.
echo [2/3] Checking if previous instances are running...
tasklist | findstr /I "java.exe" >nul
if %ERRORLEVEL% EQU 0 (
    echo WARNING: Found existing java processes
    echo If you see port already in use errors, run: stop-services.bat
    echo Or press Ctrl+C now to cancel
    timeout /t 5
)

echo.
echo [3/3] Starting CircleGuard microservices...
echo Each service will open in its own terminal window
echo.
echo Log files: logs\<service-name>.log
echo.

REM Create logs directory
if not exist logs mkdir logs

REM Define service array
set services=circleguard-auth-service
set services=!services! circleguard-identity-service
set services=!services! circleguard-form-service
set services=!services! circleguard-promotion-service
set services=!services! circleguard-notification-service
set services=!services! circleguard-gateway-service

REM Start each service in its own window
for %%S in (%services%) do (
    echo [STARTING] %%S
    start "CircleGuard - %%S" cmd /k "title CircleGuard - %%S ^&^& call ./gradlew :services:%%S:bootRun 2>&1 | tee logs\%%S.log"
    timeout /t 2 /nobreak
)

echo.
echo ================================
echo Startup sequence complete!
echo ================================
echo.
echo Services will be available at:
echo   - Auth Service:         http://localhost:8180
echo   - Identity Service:     http://localhost:8085
echo   - Form Service:         http://localhost:8086
echo   - Gateway Service:      http://localhost:8087
echo   - Promotion Service:    http://localhost:8088
echo   - Notification Service: http://localhost:8089
echo.
echo Monitor the service windows for startup completion (30-60 seconds)
echo.
echo To stop all services: run stop-services.bat
echo To view individual logs: type logs\<service-name>.log
echo.
pause
