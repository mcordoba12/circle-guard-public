# CircleGuard Development Setup Guide

## Quick Start

### Option 1: Using PowerShell (Recommended)
```powershell
# Start all services
powershell -ExecutionPolicy Bypass -File start-services.ps1

# Stop all services
powershell -ExecutionPolicy Bypass -File stop-services.ps1
```

### Option 2: Using Batch Scripts
```batch
# Start all services
start-services.bat

# Stop all services
stop-services.bat
```

## What These Scripts Do

### start-services.ps1 / start-services.bat
1. **Checks Prerequisites** - Ensures docker-compose and gradlew are available
2. **Starts Infrastructure** - Runs `docker-compose -f docker-compose.dev.yml up -d`
3. **Waits for Containers** - 15 second grace period for services to be ready
4. **Launches Services** - Opens 6 separate terminal windows:
   - circleguard-auth-service (port 8180)
   - circleguard-identity-service (port 8085)
   - circleguard-form-service (port 8086)
   - circleguard-gateway-service (port 8087)
   - circleguard-promotion-service (port 8088)
   - circleguard-notification-service (port 8089)

### stop-services.ps1 / stop-services.bat
1. **Terminates Services** - Kills all Gradle and Java processes
2. **Stops Containers** - Runs `docker-compose down`
3. **Cleans Up** - Removes PID tracking files and verifies cleanup

## Understanding the Scripts

### Why PowerShell over Batch?

| Aspect | Batch | PowerShell |
|--------|-------|-----------|
| Process Management | Limited, no PID tracking | Robust with `Get-Process` |
| Error Handling | Basic | Advanced with try/catch |
| Performance | Good | Good |
| Readability | Lower | Higher |
| Color Output | Limited | Full color support |
| **Recommendation** | ⚠️ Basic use | ✅ **Use this** |

### Key Improvements in New Scripts

**Problem**: Multiple java.exe processes spawning
- **Solution**: Opening visible windows instead of background processes
- **Benefit**: Can see startup logs and errors in real-time

**Problem**: No way to stop services cleanly
- **Solution**: Tracking PIDs in `service-pids.txt` and killing by PID
- **Benefit**: Clean shutdown without killing unrelated Java processes

**Problem**: Duplicate instances on script re-run
- **Solution**: Check for existing processes and warn user
- **Benefit**: Better control and visibility

**Problem**: Lost error messages in redirected logs
- **Solution**: See logs live in the terminal windows
- **Benefit**: Immediate visibility of startup errors

## Troubleshooting

### "Port Already in Use" Error
```powershell
# Find what's using the port
Get-NetTCPConnection -LocalPort 8180 | Select OwningProcess

# Or stop all services and Docker
.\stop-services.ps1

# Nuclear option - kill all Java processes
Stop-Process -Name java -Force
```

### Services Not Responding After Startup
1. **Check the service window** - Look for startup errors (400+ lines usually means ready)
2. **Wait longer** - Some services take 60+ seconds to fully initialize
3. **Check Docker** - Run `docker ps` to ensure containers are running
4. **Check logs** - See individual service logs in the service windows

### Script Fails to Run (PowerShell)
```powershell
# If you get execution policy error, use:
powershell -ExecutionPolicy Bypass -File start-services.ps1

# Or permanently change policy (admin required):
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### Multiple Java Processes After Stopping
```powershell
# Force kill all Java processes
taskkill /F /IM java.exe

# Then restart normally
.\start-services.ps1
```

## Monitoring and Development

### View Real-Time Logs
Each service runs in its own window with live output visible.

### View Historical Logs
```bash
# View last 50 lines of a service log
type logs\auth-service.log | tail -50

# Or on Windows PowerShell:
Get-Content logs\auth-service.log -Tail 50
```

### Check Service Health
```bash
# Auth service
curl http://localhost:8180/actuator/health

# Form service
curl http://localhost:8086/actuator/health

# Other services similarly on their ports
```

### Database Access
Services use H2 in-memory databases for testing or PostgreSQL in docker-compose.

```bash
# Check Docker containers
docker ps

# View logs from a specific container
docker logs circleguard-postgres-1

# Stop just Docker (keeps services running)
docker-compose -f docker-compose.dev.yml down
```

## Best Practices for Multi-Service Development

### 1. **Always Use the Scripts**
- Never manually start services with `./gradlew bootRun`
- Use `start-services.ps1` for consistency and proper cleanup

### 2. **Monitor Service Windows**
- Keep all 6 service windows visible
- Watch for errors during startup
- Some take 30-60 seconds to fully initialize

### 3. **Clean Shutdown**
- Use `stop-services.ps1` before stopping Docker
- Ensure all services are down before restarting
- Check `tasklist | findstr java` to verify cleanup

### 4. **Debugging Individual Services**
If you need to debug one service:
```bash
# Stop all services
.\stop-services.ps1

# Run one service with debug output
cd services/circleguard-auth-service
../../gradlew bootRun

# In another terminal, use the performance tests
cd tests/performance
locust -f locustfile.py --host=http://localhost:8180
```

### 5. **Port Conflicts**
If a port is already in use:
```bash
# Find what's using port 8180
netstat -ano | findstr :8180

# Kill that process by PID
taskkill /PID <pid> /F

# Or stop all services and restart
.\stop-services.ps1
.\start-services.ps1
```

## Performance Testing with Locust

Once all services are running:

```bash
cd tests/performance

# Light load (development)
locust -f locustfile.py --host=http://localhost:8180 --users=10 --spawn-rate=2 --run-time=2m

# Medium load
locust -f locustfile.py --host=http://localhost:8180 --users=50 --spawn-rate=5 --run-time=5m --headless

# Generate CSV report
locust -f locustfile.py --host=http://localhost:8180 --users=100 --spawn-rate=10 --run-time=10m --headless --csv=results/report
```

## Environment Variables

These are set in the service startup but can be overridden:

```bash
# Docker infrastructure settings
POSTGRES_PASSWORD=password
REDIS_PASSWORD=redis

# Services read from application.yml in src/main/resources/
spring.datasource.url=jdbc:h2:mem:testdb
spring.redis.host=localhost
```

## Useful Commands

```bash
# Start everything
.\start-services.ps1

# Stop everything
.\stop-services.ps1

# Restart everything
.\stop-services.ps1
Start-Sleep 5
.\start-services.ps1

# Check service health
curl http://localhost:8180/actuator/health

# View real-time logs
docker-compose -f docker-compose.dev.yml logs -f postgres

# List all processes by name
Get-Process | Where-Object {$_.Name -like "*java*"} | Format-Table Name, Id, Handles
```

## Architecture Overview

```
CircleGuard Microservices Architecture
├── Infrastructure (Docker)
│   ├── PostgreSQL
│   ├── Redis
│   ├── Kafka
│   └── Neo4j
├── Services
│   ├── Auth Service (8180)
│   ├── Identity Service (8085)
│   ├── Form Service (8086)
│   ├── Gateway Service (8087)
│   ├── Promotion Service (8088)
│   └── Notification Service (8089)
└── Testing
    ├── Integration Tests (via TestRestTemplate)
    └── Performance Tests (via Locust)
```

## Common Issues & Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| Port 8180 already in use | Another service running | `netstat -ano \| findstr :8180` then kill |
| "No Spring beans found" | Incomplete startup | Wait 60+ seconds |
| Connection refused (10061) | Infrastructure not ready | Wait 15 seconds after script starts |
| Duplicate java processes | Script run multiple times | Run `stop-services.ps1` first |
| logs\ folder not created | Permissions issue | Run in admin terminal |
| Docker containers won't start | Docker not running | Start Docker Desktop first |

## Next Steps

1. **Start Services**: Run `.\start-services.ps1`
2. **Monitor Windows**: Watch for "Started CircleGuard..." messages
3. **Wait for Stability**: Services take 30-60 seconds
4. **Run Tests**: Execute Locust performance tests
5. **Stop Services**: Run `.\stop-services.ps1` when done

---

**Last Updated**: 2026-04-25
**Status**: Production Ready
