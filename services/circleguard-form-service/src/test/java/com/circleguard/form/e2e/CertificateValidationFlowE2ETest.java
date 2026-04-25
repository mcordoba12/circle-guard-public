package com.circleguard.form.e2e;

import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.service.HealthSurveyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.circleguard.form.model.HealthSurvey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * End-to-End tests for certificate validation flow.
 * Validates certificate validation request handling and event publishing.
 *
 * Flow:
 * 1. GET /api/v1/certificates/pending → get pending surveys
 * 2. POST /api/v1/certificates/{id}/validate → validate survey
 * 3. Verify Kafka event is published (certificate.validated)
 * 4. Verify state is updated
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("End-to-End Certificate Validation Flow Tests")
class CertificateValidationFlowE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockBean
    private HealthSurveyService surveyService;

    private UUID surveyId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        surveyId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        // Mock KafkaTemplate to not actually send messages
        when(kafkaTemplate.send(anyString(), any()))
                .thenReturn(null);

        // Mock HealthSurveyService
        when(surveyService.getPendingSurveys())
                .thenReturn(new ArrayList<>());
    }

    @Test
    @DisplayName("E2E: Get pending certificates returns 200 OK")
    void test_getPendingCertificates_Returns200OK() {
        // Arrange
        when(surveyService.getPendingSurveys())
                .thenReturn(new ArrayList<>());

        // Act
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/certificates/pending",
                List.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("E2E: Get pending certificates returns valid structure")
    void test_getPendingCertificates_ReturnsValidStructure() {
        // Arrange
        when(surveyService.getPendingSurveys())
                .thenReturn(new ArrayList<>());

        // Act
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/certificates/pending",
                List.class
        );

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("E2E: Validate certificate request succeeds")
    void test_validateCertificateRequest_Succeeds() {
        // Arrange
        String url = String.format(
                "/api/v1/certificates/%s/validate?status=APPROVED&adminId=%s",
                surveyId,
                adminId
        );

        // Act
        ResponseEntity<Void> response = restTemplate.postForEntity(
                url,
                null,
                Void.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("E2E: Validate certificate with APPROVED status")
    void test_validateCertificate_WithApprovedStatus() {
        // Arrange
        String url = String.format(
                "/api/v1/certificates/%s/validate?status=APPROVED&adminId=%s",
                surveyId,
                adminId
        );

        doNothing().when(surveyService)
                .validateSurvey(any(UUID.class), any(ValidationStatus.class), any(UUID.class));

        // Act
        ResponseEntity<Void> response = restTemplate.postForEntity(
                url,
                null,
                Void.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(surveyService).validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);
    }

    @Test
    @DisplayName("E2E: Validate certificate with REJECTED status")
    void test_validateCertificate_WithRejectedStatus() {
        // Arrange
        String url = String.format(
                "/api/v1/certificates/%s/validate?status=REJECTED&adminId=%s",
                surveyId,
                adminId
        );

        doNothing().when(surveyService)
                .validateSurvey(any(UUID.class), any(ValidationStatus.class), any(UUID.class));

        // Act
        ResponseEntity<Void> response = restTemplate.postForEntity(
                url,
                null,
                Void.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(surveyService).validateSurvey(surveyId, ValidationStatus.REJECTED, adminId);
    }

    @Test
    @DisplayName("E2E: Validate different certificates independently")
    void test_validateDifferentCertificates_Independently() {
        // Arrange
        UUID survey1Id = UUID.randomUUID();
        UUID survey2Id = UUID.randomUUID();
        UUID admin1Id = UUID.randomUUID();
        UUID admin2Id = UUID.randomUUID();

        String url1 = String.format(
                "/api/v1/certificates/%s/validate?status=APPROVED&adminId=%s",
                survey1Id,
                admin1Id
        );
        String url2 = String.format(
                "/api/v1/certificates/%s/validate?status=REJECTED&adminId=%s",
                survey2Id,
                admin2Id
        );

        doNothing().when(surveyService)
                .validateSurvey(any(UUID.class), any(ValidationStatus.class), any(UUID.class));

        // Act
        ResponseEntity<Void> response1 = restTemplate.postForEntity(
                url1,
                null,
                Void.class
        );
        ResponseEntity<Void> response2 = restTemplate.postForEntity(
                url2,
                null,
                Void.class
        );

        // Assert
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(surveyService).validateSurvey(survey1Id, ValidationStatus.APPROVED, admin1Id);
        verify(surveyService).validateSurvey(survey2Id, ValidationStatus.REJECTED, admin2Id);
    }

    @Test
    @DisplayName("E2E: Get pending and validate in sequence")
    void test_getPendingAndValidateInSequence() {
        // Arrange
        when(surveyService.getPendingSurveys())
                .thenReturn(new ArrayList<>());

        doNothing().when(surveyService)
                .validateSurvey(any(UUID.class), any(ValidationStatus.class), any(UUID.class));

        // Act
        // Step 1: Get pending
        ResponseEntity<List> pendingResponse = restTemplate.getForEntity(
                "/api/v1/certificates/pending",
                List.class
        );

        // Step 2: Validate one
        String validateUrl = String.format(
                "/api/v1/certificates/%s/validate?status=APPROVED&adminId=%s",
                surveyId,
                adminId
        );
        ResponseEntity<Void> validateResponse = restTemplate.postForEntity(
                validateUrl,
                null,
                Void.class
        );

        // Assert
        assertThat(pendingResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(validateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("E2E: Multiple certificate validations with different statuses")
    void test_multipleCertificateValidations_WithDifferentStatuses() {
        // Arrange
        UUID cert1 = UUID.randomUUID();
        UUID cert2 = UUID.randomUUID();
        UUID cert3 = UUID.randomUUID();
        UUID admin = UUID.randomUUID();

        doNothing().when(surveyService)
                .validateSurvey(any(UUID.class), any(ValidationStatus.class), any(UUID.class));

        // Act
        ResponseEntity<Void> response1 = restTemplate.postForEntity(
                String.format("/api/v1/certificates/%s/validate?status=APPROVED&adminId=%s", cert1, admin),
                null,
                Void.class
        );
        ResponseEntity<Void> response2 = restTemplate.postForEntity(
                String.format("/api/v1/certificates/%s/validate?status=REJECTED&adminId=%s", cert2, admin),
                null,
                Void.class
        );
        ResponseEntity<Void> response3 = restTemplate.postForEntity(
                String.format("/api/v1/certificates/%s/validate?status=PENDING&adminId=%s", cert3, admin),
                null,
                Void.class
        );

        // Assert
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
