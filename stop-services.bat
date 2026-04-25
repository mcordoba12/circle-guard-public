@echo off
REM stop-services.bat - Stop CircleGuard microservices and infrastructure

setlocal enabledelayedexpansion

echo.
echo ================================
echo CircleGuard Services Shutdown
echo ================================
echo.

REM Step 1: Kill Gradle processes (gradlew)
echo [1/3] Stopping Gradle processes...
taskkill /F /IM gradle.exe 2>nul
taskkill /F /IM java.exe 2>nul

REM Wait a bit for processes to terminate
timeout /t 3 /nobreak

REM Step 2: Verify all Java processes are stopped
tasklist | findstr /I "java.exe" >nul
if %ERRORLEVEL% EQU 0 (
    echo WARNING: Some Java processes are still running
    echo Attempting force kill...
    taskkill /F /IM java.exe /T
    timeout /t 2 /nobreak
)

echo.
echo [2/3] Stopping Docker containers...
docker-compose -f docker-compose.dev.yml down

if %ERRORLEVEL% NEQ 0 (
    echo WARNING: Could not stop Docker containers cleanly
    echo You may need to stop them manually
)

echo.
echo [3/3] Cleanup complete
echo.
echo ================================
echo Services stopped successfully!
echo ================================
echo.
echo Remaining processes (if any):
tasklist | findstr /I "java.exe"
echo.
echo To restart services, run: start-services.bat
echo.

pause
