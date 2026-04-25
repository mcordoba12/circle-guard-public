"""
CircleGuard Performance Tests with Locust

This load testing suite simulates realistic user scenarios in the CircleGuard
contact tracing system. It includes 4 different user types with varying
behaviors and weights to realistically model system usage.

Scenarios:
1. CampusEntryUser (40%) - Students entering campus in morning
2. HealthReportUser (20%) - Daily symptom reporting
3. HealthStatusUser (30%) - Frequent health status queries
4. AdminUser (10%) - Administrative audits

Run with:
    locust -f locustfile.py --host=http://localhost:8180 --users=100 --spawn-rate=10 --run-time=5m
"""

import os
import random
import uuid
from time import time
from locust import HttpUser, task, between, events


# Configuration
HOST = os.getenv("HOST", "http://localhost:8180")
FORM_SERVICE_HOST = os.getenv("FORM_SERVICE_HOST", "http://localhost:8086")
GATEWAY_SERVICE_HOST = os.getenv("GATEWAY_SERVICE_HOST", "http://localhost:8087")
PROMOTION_SERVICE_HOST = os.getenv("PROMOTION_SERVICE_HOST", "http://localhost:8088")

# Test credentials (from V2__seed_test_users.sql)
TEST_USERNAME = "staff_guard"
TEST_PASSWORD = "password"

# Performance tracking
slow_requests = {"count": 0, "threshold_ms": 2000}


class CircleGuardUser(HttpUser):
    """
    Base class for all CircleGuard user types.
    Handles authentication and common setup.
    """

    abstract = True
    wait_time = between(1, 2)

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.token = None
        self.anonymous_id = None
        self.qr_token = None

    def on_start(self):
        """Called when a user starts. Performs login."""
        self.login()

    def login(self):
        """
        Authenticate user and obtain JWT token.
        Saves token for subsequent requests.
        """
        login_data = {
            "username": TEST_USERNAME,
            "password": TEST_PASSWORD
        }

        try:
            response = self.client.post(
                f"{HOST}/api/v1/auth/login",
                json=login_data,
                timeout=30
            )

            if response.status_code == 200:
                data = response.json()
                self.token = data.get("token")
                self.anonymous_id = data.get("anonymousId")
            else:
                print(f"Login failed with status {response.status_code}")
                self.environment.runner.quit()
        except Exception as e:
            print(f"Login error: {e}")
            self.environment.runner.quit()

    def get_auth_headers(self):
        """Returns headers with Bearer token for authenticated requests."""
        if not self.token:
            return {}
        return {
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json"
        }

    def log_request_time(self, name, response_time_ms, success):
        """Log request timing and detect slow requests."""
        if response_time_ms > slow_requests["threshold_ms"]:
            slow_requests["count"] += 1


class CampusEntryUser(CircleGuardUser):
    """
    Simulates students/staff entering campus in morning rush (8-9am).
    Follows: login → get QR token → validate token at gate.
    Weight: 40%
    """

    wait_time = between(1, 3)
    weight = 40

    @task
    def gate_entry_flow(self):
        """Complete gate entry workflow."""
        start_time = time()

        try:
            # Step 1: Get QR Token
            response = self.client.get(
                f"{HOST}/api/v1/auth/qr/generate",
                headers=self.get_auth_headers(),
                timeout=30,
                name="GET /api/v1/auth/qr/generate"
            )

            if response.status_code != 200:
                return

            data = response.json()
            self.qr_token = data.get("qrToken")

            # Step 2: Validate token at gateway
            validation_data = {"token": self.qr_token}
            response = self.client.post(
                f"{GATEWAY_SERVICE_HOST}/api/v1/gate/validate",
                json=validation_data,
                timeout=30,
                name="POST /api/v1/gate/validate"
            )

        except Exception as e:
            print(f"Gate entry flow error: {e}")


class HealthReportUser(CircleGuardUser):
    """
    Simulates daily health symptom reporting.
    Submits survey with symptoms and vaccination status.
    Weight: 20%
    """

    wait_time = between(2, 5)
    weight = 20

    @task
    def submit_health_survey(self):
        """Submit daily health survey."""
        try:
            # Use authenticated user's anonymous ID if available, otherwise generate one
            random_id = self.anonymous_id if self.anonymous_id else str(uuid.uuid4())

            survey_data = {
                "anonymousId": random_id,
                "hasFever": random.choice([True, False]),
                "hasCough": random.choice([True, False]),
                "otherSymptoms": random.choice([
                    "sore throat",
                    "nasal congestion",
                    "body aches",
                    ""
                ])
            }

            response = self.client.post(
                f"{FORM_SERVICE_HOST}/api/v1/surveys",
                json=survey_data,
                headers=self.get_auth_headers(),
                timeout=30,
                name="POST /api/v1/surveys"
            )

        except Exception as e:
            print(f"Health survey error: {e}")


class HealthStatusUser(CircleGuardUser):
    """
    Simulates frequent health status and contact circle queries.
    Typical user checking their health status multiple times per day.
    Weight: 30%
    """

    wait_time = between(1, 2)
    weight = 30

    @task(2)
    def check_health_status(self):
        """Check personal health status."""
        try:
            health_data = {
                "anonymousId": self.anonymous_id if self.anonymous_id else str(uuid.uuid4()),
                "status": "HEALTHY"
            }
            response = self.client.post(
                f"{PROMOTION_SERVICE_HOST}/api/v1/health/report",
                json=health_data,
                headers=self.get_auth_headers(),
                timeout=30,
                name="POST /api/v1/health/report"
            )
        except Exception as e:
            print(f"Health status error: {e}")

    @task(1)
    def check_contact_circles(self):
        """Check contact circles/groups."""
        try:
            if not self.anonymous_id:
                return
            response = self.client.get(
                f"{PROMOTION_SERVICE_HOST}/api/v1/circles/user/{self.anonymous_id}",
                headers=self.get_auth_headers(),
                timeout=30,
                name="GET /api/v1/circles/user/{anonymousId}"
            )
        except Exception as e:
            print(f"Contact circles error: {e}")


class AdminUser(CircleGuardUser):
    """
    Simulates administrative audits and monitoring.
    Lower weight to reflect less frequent admin activity.
    Weight: 10%
    """

    wait_time = between(3, 7)
    weight = 10

    @task(1)
    def audit_circles(self):
        """Retrieve system settings for audit."""
        try:
            response = self.client.get(
                f"{PROMOTION_SERVICE_HOST}/api/v1/admin/settings",
                headers=self.get_auth_headers(),
                timeout=30,
                name="GET /api/v1/admin/settings"
            )
        except Exception as e:
            print(f"Admin settings error: {e}")

    @task(1)
    def audit_health_stats(self):
        """Retrieve health statistics for audit."""
        try:
            response = self.client.get(
                f"{PROMOTION_SERVICE_HOST}/api/v1/health-status/stats",
                headers=self.get_auth_headers(),
                timeout=30,
                name="GET /api/v1/health-status/stats"
            )
        except Exception as e:
            print(f"Admin health stats error: {e}")


# Event listeners for metrics
@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    """Called when load test starts."""
    print(f"\n{'='*70}")
    print(f"CircleGuard Performance Test Started")
    print(f"{'='*70}")
    print(f"Auth Service: {HOST}")
    print(f"Form Service: {FORM_SERVICE_HOST}")
    print(f"Gateway Service: {GATEWAY_SERVICE_HOST}")
    print(f"Promotion Service: {PROMOTION_SERVICE_HOST}")
    print(f"{'='*70}\n")


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    """Called when load test stops."""
    print(f"\n{'='*70}")
    print(f"CircleGuard Performance Test Completed")
    print(f"{'='*70}")
    print(f"Total slow requests (>2000ms): {slow_requests['count']}")
    print(f"{'='*70}\n")
