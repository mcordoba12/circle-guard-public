# CircleGuard Performance Testing with Locust

Load testing suite for CircleGuard contact tracing system. Simulates realistic user scenarios to measure system performance under load.

## Installation

```bash
# Install dependencies
pip install -r requirements.txt
```

## Quick Start

### Run Against Local Services (Development)

```bash
# Basic run with 100 concurrent users, spawn rate of 10 users/second, 5 minute duration
locust -f locustfile.py \
  --host=http://localhost:8180 \
  --users=100 \
  --spawn-rate=10 \
  --run-time=5m
```

Then open http://localhost:8089 in your browser to view the UI.

### Run in Headless Mode (No UI)

```bash
locust -f locustfile.py \
  --headless \
  --host=http://localhost:8180 \
  --users=100 \
  --spawn-rate=10 \
  --run-time=5m
```

### Run with Custom Service Hosts

```bash
locust -f locustfile.py \
  --host=http://localhost:8180 \
  --users=100 \
  --spawn-rate=10 \
  --run-time=5m \
  -e HOST=http://localhost:8180 \
  -e FORM_SERVICE_HOST=http://localhost:8086 \
  -e GATEWAY_SERVICE_HOST=http://localhost:8087 \
  -e PROMOTION_SERVICE_HOST=http://localhost:8088
```

### Run Different Load Profiles

**Light Load (Development Testing)**
```bash
locust -f locustfile.py --host=http://localhost:8180 --users=10 --spawn-rate=2 --run-time=2m
```

**Medium Load (Staging Testing)**
```bash
locust -f locustfile.py --host=http://localhost:8180 --users=50 --spawn-rate=5 --run-time=10m
```

**Heavy Load (Performance Baseline)**
```bash
locust -f locustfile.py --host=http://localhost:8180 --users=200 --spawn-rate=20 --run-time=15m
```

**Stress Testing (Push to Limits)**
```bash
locust -f locustfile.py --host=http://localhost:8180 --users=500 --spawn-rate=50 --run-time=20m
```

## User Scenarios

The test suite includes 4 realistic user types with different behaviors:

### 1. CampusEntryUser (40% of traffic)
**Simulates:** Students/staff entering campus in morning rush (8-9am)

**Behavior:**
- Login → Get QR token → Validate at gateway
- Wait time: 1-3 seconds between actions
- High concurrent spike during morning hours

**Endpoints:**
- POST /api/v1/auth/login
- GET /api/v1/auth/qr-token
- POST /api/v1/gate/validate

**Why This Matters:**
- Gateway service experiences peak load in morning
- Tests authentication + QR validation flow
- Critical path for campus access control

### 2. HealthReportUser (20% of traffic)
**Simulates:** Daily symptom reporting throughout the day

**Behavior:**
- Submit health survey with symptoms
- Includes optional symptoms like fever, cough
- Wait time: 2-5 seconds between submissions

**Endpoints:**
- POST /api/v1/surveys

**Why This Matters:**
- Tests form service under steady load
- Validates data persistence
- Generates Kafka events for promotion service

### 3. HealthStatusUser (30% of traffic)
**Simulates:** Frequent user checking their health status

**Behavior:**
- Check personal health status (2x more frequent)
- Check contact circles/groups (1x)
- Wait time: 1-2 seconds between queries
- Represents typical app usage patterns

**Endpoints:**
- GET /api/v1/health-status/me
- GET /api/v1/circles

**Why This Matters:**
- Heavy read operations
- Tests promotion service graph queries
- Most common user behavior

### 4. AdminUser (10% of traffic)
**Simulates:** Administrative audits and monitoring

**Behavior:**
- Audit all circles
- Retrieve health statistics
- Wait time: 3-7 seconds between actions
- Lower frequency, reflects admin reality

**Endpoints:**
- GET /api/v1/admin/circles
- GET /api/v1/health-status/stats

**Why This Matters:**
- Tests admin endpoints
- Validates data aggregation
- Large result sets

## Performance Metrics Explained

### Key Metrics

**RPS (Requests Per Second)**
- How many requests the system processes per second
- Higher is better
- Indicates throughput capacity

**Response Time Percentiles (P50, P95, P99)**
- **P50 (Median):** 50% of requests are faster than this
- **P95:** 95% of requests are faster than this (key SLA metric)
- **P99:** 99% of requests are faster than this (tail latency)

**Error Rate**
- Percentage of failed requests
- Should stay below thresholds
- Indicates system stability under load

### Example Metrics Interpretation

```
Type          | Name              | # reqs  | # fails | Median | P95  | P99   | RPS
--------------+-------------------+---------+---------+--------+------+-------+-------
http          | GET /api/v1/...   | 5000    | 0       | 45 ms  | 120  | 250   | 50.0
http          | POST /api/v1/...  | 2500    | 1 (0%) | 80 ms  | 180  | 350   | 25.0
--------------+-------------------+---------+---------+--------+------+-------+-------
TOTAL         | -                 | 7500    | 1 (0%) | 55 ms  | 140  | 280   | 75.0
```

**This means:**
- 7,500 requests executed successfully
- Only 1 error (0.01% error rate) ✅
- Median response: 55ms ✅
- P95 response: 140ms ✅
- System handling 75 requests/second ✅

## Acceptable Thresholds

### Development Environment

```
✓ P95 Response Time < 2000ms
✓ Error Rate < 5%
✓ RPS > 10 (minimum throughput)
```

### Staging Environment

```
✓ P95 Response Time < 1500ms
✓ Error Rate < 2%
✓ RPS > 50
```

### Production/Master Branch

```
✓ P95 Response Time < 1000ms
✓ Error Rate < 1%
✓ RPS > 100
```

## Advanced Usage

### Generate CSV Report

```bash
locust -f locustfile.py \
  --headless \
  --host=http://localhost:8180 \
  --users=100 \
  --spawn-rate=10 \
  --run-time=5m \
  --csv=results
```

Creates `results_stats.csv` and `results_failures.csv`

### Set Custom Parameters

```bash
locust -f locustfile.py \
  --host=http://localhost:8180 \
  --users=100 \
  --spawn-rate=10 \
  --run-time=5m \
  --stop-timeout=10
```

### Distributed Testing (Multiple Machines)

**Master Node:**
```bash
locust -f locustfile.py \
  --master \
  --host=http://localhost:8180 \
  --users=1000
```

**Worker Nodes (on different machines):**
```bash
locust -f locustfile.py \
  --worker \
  --master-host=master.example.com \
  --host=http://localhost:8180
```

## Interpreting Results

### Good Performance ✅
```
- P95 < 1500ms
- Error rate < 2%
- No slow requests warnings
- Consistent RPS across test duration
```

### Performance Issues ⚠️
```
- P95 > 2000ms → Server struggling, investigate bottlenecks
- Error rate > 5% → System instability, check logs
- Increasing P95 over time → Memory leak or connection issues
- Spikes in errors → Service dependency failure
```

### Common Bottlenecks

| Symptom | Likely Cause | Solution |
|---------|--------------|----------|
| High P95 but low error rate | Database slow | Add indexes, optimize queries |
| Increasing errors over time | Connection pool exhausted | Increase pool size, check leaks |
| High CPU, P95 increases | Processing heavy task | Optimize algorithm, add caching |
| Error spike at high load | Resource limit hit | Scale horizontally, increase limits |

## Test Scenarios by Use Case

### Morning Campus Rush (8-9am)
```bash
# Heavy CampusEntryUser load
locust -f locustfile.py --host=http://localhost:8180 --users=500 --spawn-rate=50 --run-time=30m
```

### Typical Weekday Usage
```bash
# Balanced mix of all user types
locust -f locustfile.py --host=http://localhost:8180 --users=200 --spawn-rate=20 --run-time=60m
```

### Survey Campaign Day
```bash
# Heavy HealthReportUser load
locust -f locustfile.py --host=http://localhost:8180 --users=300 --spawn-rate=30 --run-time=45m
```

## Troubleshooting

### "Connection refused" errors
- Check if services are running on specified ports
- Verify `HOST` and service host variables
- Check firewall rules

### "Authentication failed" errors
- Verify test credentials in locustfile.py
- Check if auth-service is accepting connections
- Review auth-service logs

### High error rates
- Check service logs for exceptions
- Verify database connections
- Check external service dependencies (Kafka, Redis, Neo4j)

### Tests exit immediately
- Check user on_start() method for errors
- Verify login credentials are correct
- Review error logs in Locust console

## Configuration

Edit `locustfile.py` to customize:

```python
# Change test credentials
TEST_USERNAME = "your_user"
TEST_PASSWORD = "your_pass"

# Change slow request threshold
slow_requests = {"count": 0, "threshold_ms": 2000}
```

## Performance Testing Best Practices

1. **Test in isolation**: Run tests against dedicated test environment
2. **Baseline first**: Establish baseline before making changes
3. **Gradual ramp**: Increase load gradually to find breaking point
4. **Multiple runs**: Run tests 3+ times to ensure consistency
5. **Monitor resources**: Watch CPU, memory, disk during tests
6. **Review logs**: Check application logs for errors
7. **Document results**: Keep records for trend analysis

## Related Documentation

- [CircleGuard Architecture](../../docs/ARCHITECTURE.md)
- [Deployment Guide](../../docs/DEPLOYMENT.md)
- [Monitoring & Observability](../../docs/MONITORING.md)

## Support

For issues or questions about performance testing:
1. Check this README
2. Review Locust documentation: https://docs.locust.io
3. Check application logs for errors
4. Profile individual services if needed
