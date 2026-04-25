@echo off
REM CircleGuard Docker Image Builder (Windows)
REM Estrategia: Compilar localmente con Gradle, luego dockerizar
REM Paso 1: ./gradlew bootJar -x test
REM Paso 2: Este script construye imágenes Docker

setlocal enabledelayedexpansion

set REGISTRY=circleguard

REM Get the project root (remove trailing backslash)
set "PROJECT_ROOT=%~dp0"
if "%PROJECT_ROOT:~-1%"=="\" set "PROJECT_ROOT=%PROJECT_ROOT:~0,-1%"

cls
echo.
echo ================================================================
echo CircleGuard Docker Image Builder (Windows)
echo ================================================================
echo.
echo Project Root: %PROJECT_ROOT%
echo.

REM Verify Docker is running
docker info >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker is not running. Please start Docker Desktop.
    pause
    exit /b 1
)

echo [OK] Docker is running
echo.

REM Change to project root
cd /d "%PROJECT_ROOT%"
if errorlevel 1 (
    echo [ERROR] Failed to change directory to project root
    pause
    exit /b 1
)

echo [*] Checking if JARs are pre-built...
echo.

REM Check if JAR files exist
set JAR_COUNT=0
for %%i in (services\*/build/libs/*-1.0.0-SNAPSHOT.jar) do (
    set /a JAR_COUNT+=1
)

if %JAR_COUNT% lss 6 (
    echo [!] JARs not found. You need to build them first:
    echo.
    echo    ./gradlew bootJar -x test
    echo.
    pause
    exit /b 1
)

echo [OK] Found %JAR_COUNT% pre-built JARs
echo.

set BUILD_COUNT=0
set FAILED=0

REM Build each service
call :build_service "circleguard-auth-service" "8180"
call :build_service "circleguard-identity-service" "8083"
call :build_service "circleguard-form-service" "8086"
call :build_service "circleguard-promotion-service" "8088"
call :build_service "circleguard-notification-service" "8082"
call :build_service "circleguard-gateway-service" "8087"

REM Summary
echo.
echo ================================================================
echo Build Summary
echo ================================================================
echo.
echo [OK] Successfully built: %BUILD_COUNT%/6 images
echo [FAILED] %FAILED% images
echo.

if %BUILD_COUNT% equ 6 (
    echo All images built successfully!
    echo.
    echo Available images:
    echo    - circleguard/circleguard-auth-service:latest (port 8180)
    echo    - circleguard/circleguard-identity-service:latest (port 8083)
    echo    - circleguard/circleguard-form-service:latest (port 8086)
    echo    - circleguard/circleguard-promotion-service:latest (port 8088)
    echo    - circleguard/circleguard-notification-service:latest (port 8082)
    echo    - circleguard/circleguard-gateway-service:latest (port 8087)
    echo.
    echo To run a container:
    echo   docker run -p 8180:8180 circleguard/circleguard-auth-service:latest
    echo.
)

pause
exit /b 0

REM Function to build a service
:build_service
set "SERVICE_NAME=%~1"
set "SERVICE_PORT=%~2"
set "IMAGE_NAME=%REGISTRY%/%SERVICE_NAME%:latest"
set "DOCKERFILE_PATH=%PROJECT_ROOT%\services\%SERVICE_NAME%\Dockerfile"
set "JAR_PATH=%PROJECT_ROOT%\services\%SERVICE_NAME%\build\libs\%SERVICE_NAME%-1.0.0-SNAPSHOT.jar"

echo ----
echo Building: !IMAGE_NAME!
echo Port: !SERVICE_PORT!
echo.

REM Check if Dockerfile exists
if not exist "!DOCKERFILE_PATH!" (
    echo [ERROR] Dockerfile not found: !DOCKERFILE_PATH!
    set /a FAILED+=1
    goto :eof
)

REM Check if JAR exists
if not exist "!JAR_PATH!" (
    echo [ERROR] JAR not found: !JAR_PATH!
    echo [INFO] Run: ./gradlew bootJar -x test
    set /a FAILED+=1
    goto :eof
)

REM Build image
echo [*] Building Docker image...
docker build --file "services\!SERVICE_NAME!\Dockerfile" --tag "!IMAGE_NAME!" .

if errorlevel 1 (
    echo [ERROR] Failed to build !IMAGE_NAME!
    set /a FAILED+=1
) else (
    echo [OK] Successfully built: !IMAGE_NAME!
    set /a BUILD_COUNT+=1
)

echo.
goto :eof
