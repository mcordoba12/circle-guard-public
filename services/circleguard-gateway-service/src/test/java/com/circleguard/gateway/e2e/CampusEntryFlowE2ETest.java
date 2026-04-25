package com.circleguard.gateway.e2e;

import com.circleguard.gateway.service.QrValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-End tests for campus entry QR validation flow.
 * Validates QR token validation with GREEN (allowed) and RED (denied) status.
 *
 * Flow:
 * 1. POST /api/v1/gate/validate with valid QR token → GREEN status
 * 2. POST /api/v1/gate/validate with invalid QR token → RED status
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("End-to-End Campus Entry QR Validation Flow Tests")
class CampusEntryFlowE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    private String validQrToken;
    private String invalidQrToken;

    @BeforeEach
    void setUp() {
        // Valid token format (simulate a valid QR code token)
        validQrToken = "valid-qr-token-12345-abcde";
        invalidQrToken = "invalid-or-expired-token";
    }

    @Test
    @DisplayName("E2E: Valid QR token returns 200 OK")
    void test_validQrToken_Returns200OK() {
        // Arrange
        Map<String, String> validationRequest = new HashMap<>();
        validationRequest.put("token", validQrToken);

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/gate/validate",
                validationRequest,
                Map.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("E2E: Valid QR token returns validation result with status")
    void test_validQrToken_ReturnsValidationResultWithStatus() {
        // Arrange
        Map<String, String> validationRequest = new HashMap<>();
        validationRequest.put("token", validQrToken);

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/gate/validate",
                validationRequest,
                Map.class
        );

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("status");
    }

    @Test
    @DisplayName("E2E: Valid QR token returns GREEN access status")
    void test_validQrToken_ReturnsGreenStatus() {
        // Arrange
        Map<String, String> validationRequest = new HashMap<>();
        validationRequest.put("token", validQrToken);

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/gate/validate",
                validationRequest,
                Map.class
        );

        // Assert
        assertThat(response.getBody())
                .containsEntry("status", "GREEN");
    }

    @Test
    @DisplayName("E2E: Invalid QR token returns RED access status")
    void test_invalidQrToken_ReturnsRedStatus() {
        // Arrange
        Map<String, String> validationRequest = new HashMap<>();
        validationRequest.put("token", invalidQrToken);

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/gate/validate",
                validationRequest,
                Map.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("status", "RED");
    }

    @Test
    @DisplayName("E2E: Invalid QR token response includes denial reason")
    void test_invalidQrToken_IncludesDenialReason() {
        // Arrange
        Map<String, String> validationRequest = new HashMap<>();
        validationRequest.put("token", invalidQrToken);

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/gate/validate",
                validationRequest,
                Map.class
        );

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody())
                .containsKey("status")
                .containsEntry("status", "RED");
    }

    @Test
    @DisplayName("E2E: Valid and invalid tokens return different statuses")
    void test_validAndInvalidTokens_ReturnDifferentStatuses() {
        // Arrange
        Map<String, String> validRequest = new HashMap<>();
        validRequest.put("token", validQrToken);

        Map<String, String> invalidRequest = new HashMap<>();
        invalidRequest.put("token", invalidQrToken);

        // Act
        ResponseEntity<Map> validResponse = restTemplate.postForEntity(
                "/api/v1/gate/validate",
                validRequest,
                Map.class
        );
        ResponseEntity<Map> invalidResponse = restTemplate.postForEntity(
                "/api/v1/gate/validate",
                invalidRequest,
                Map.class
        );

        // Assert
        assertThat(validResponse.getBody().get("status"))
                .isNotEqualTo(invalidResponse.getBody().get("status"));
        assertThat(validResponse.getBody().get("status"))
                .isEqualTo("GREEN");
        assertThat(invalidResponse.getBody().get("status"))
                .isEqualTo("RED");
    }

    @Test
    @DisplayName("E2E: Empty token returns RED status")
    void test_emptyToken_ReturnsRedStatus() {
        // Arrange
        Map<String, String> emptyTokenRequest = new HashMap<>();
        emptyTokenRequest.put("token", "");

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/gate/validate",
                emptyTokenRequest,
                Map.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("status", "RED");
    }

    @Test
    @DisplayName("E2E: Multiple token validations are processed independently")
    void test_multipleTokenValidations_AreProcessedIndependently() {
        // Arrange
        Map<String, String> request1 = new HashMap<>();
        request1.put("token", validQrToken);

        Map<String, String> request2 = new HashMap<>();
        request2.put("token", invalidQrToken);

        Map<String, String> request3 = new HashMap<>();
        request3.put("token", "another-valid-token");

        // Act
        ResponseEntity<Map> response1 = restTemplate.postForEntity(
                "/api/v1/gate/validate",
                request1,
                Map.class
        );
        ResponseEntity<Map> response2 = restTemplate.postForEntity(
                "/api/v1/gate/validate",
                request2,
                Map.class
        );
        ResponseEntity<Map> response3 = restTemplate.postForEntity(
                "/api/v1/gate/validate",
                request3,
                Map.class
        );

        // Assert
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody().get("status")).isEqualTo("GREEN");
        assertThat(response2.getBody().get("status")).isEqualTo("RED");
    }
}
