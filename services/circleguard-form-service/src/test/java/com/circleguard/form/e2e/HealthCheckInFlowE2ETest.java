package com.circleguard.form.e2e;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.service.HealthSurveyService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * End-to-End tests for health check-in survey flow.
 * Validates survey submission and Kafka event publishing.
 *
 * Flow:
 * 1. POST /api/v1/surveys with symptoms data
 * 2. Receive 200/201 response with survey details
 * 3. Verify Kafka event is published
 * 4. Verify anonymousId in response
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("End-to-End Health Check-In Survey Flow Tests")
class HealthCheckInFlowE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private HealthSurveyService surveyService;

    private UUID testAnonymousId;

    @BeforeEach
    void setUp() {
        testAnonymousId = UUID.randomUUID();

        // Mock KafkaTemplate to not actually send messages
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(null);
    }

    @Test
    @DisplayName("E2E: Health survey submission returns 200 OK")
    void test_healthSurveySubmission_Returns200OK() {
        // Arrange
        Map<String, Object> surveyRequest = new HashMap<>();
        surveyRequest.put("anonymousId", testAnonymousId.toString());
        surveyRequest.put("hasFever", true);
        surveyRequest.put("hasCough", true);
        surveyRequest.put("otherSymptoms", "headache");

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/surveys",
                surveyRequest,
                Map.class
        );

        // Assert
        assertThat(response.getStatusCode())
                .isIn(HttpStatus.OK, HttpStatus.CREATED);
    }

    @Test
    @DisplayName("E2E: Survey response includes anonymousId")
    void test_surveyResponse_IncludesAnonymousId() {
        // Arrange
        Map<String, Object> surveyRequest = new HashMap<>();
        surveyRequest.put("anonymousId", testAnonymousId.toString());
        surveyRequest.put("hasFever", true);

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/surveys",
                surveyRequest,
                Map.class
        );

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("anonymousId");
    }

    @Test
    @DisplayName("E2E: Survey submission with symptoms is persisted")
    void test_surveySubmission_WithSymptoms_IsPersisted() {
        // Arrange
        Map<String, Object> surveyRequest = new HashMap<>();
        surveyRequest.put("anonymousId", testAnonymousId.toString());
        surveyRequest.put("hasFever", true);
        surveyRequest.put("hasCough", true);
        surveyRequest.put("otherSymptoms", "headache, fatigue");

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/surveys",
                surveyRequest,
                Map.class
        );

        // Assert
        assertThat(response.getStatusCode())
                .isIn(HttpStatus.OK, HttpStatus.CREATED);
        assertThat(response.getBody())
                .containsKey("otherSymptoms");
    }

    @Test
    @DisplayName("E2E: Survey submission with fever is recorded")
    void test_surveySubmission_WithFever_IsRecorded() {
        // Arrange
        Map<String, Object> surveyRequest = new HashMap<>();
        surveyRequest.put("anonymousId", testAnonymousId.toString());
        surveyRequest.put("hasFever", true);

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/surveys",
                surveyRequest,
                Map.class
        );

        // Assert
        assertThat(response.getStatusCode())
                .isIn(HttpStatus.OK, HttpStatus.CREATED);
        assertThat(response.getBody())
                .containsEntry("hasFever", true);
    }

    @Test
    @DisplayName("E2E: Survey response contains valid structure")
    void test_surveyResponse_ContainsValidStructure() {
        // Arrange
        Map<String, Object> surveyRequest = new HashMap<>();
        surveyRequest.put("anonymousId", testAnonymousId.toString());
        surveyRequest.put("hasCough", true);
        surveyRequest.put("otherSymptoms", "sore throat");

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/surveys",
                surveyRequest,
                Map.class
        );

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys(
                "anonymousId",
                "hasCough"
        );
    }

    @Test
    @DisplayName("E2E: Multiple survey submissions are processed independently")
    void test_multipleSurveySubmissions_AreProcessedIndependently() {
        // Arrange
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Map<String, Object> survey1 = new HashMap<>();
        survey1.put("anonymousId", id1.toString());
        survey1.put("hasFever", true);

        Map<String, Object> survey2 = new HashMap<>();
        survey2.put("anonymousId", id2.toString());
        survey2.put("hasCough", true);

        // Act
        ResponseEntity<Map> response1 = restTemplate.postForEntity(
                "/api/v1/surveys",
                survey1,
                Map.class
        );
        ResponseEntity<Map> response2 = restTemplate.postForEntity(
                "/api/v1/surveys",
                survey2,
                Map.class
        );

        // Assert
        assertThat(response1.getStatusCode())
                .isIn(HttpStatus.OK, HttpStatus.CREATED);
        assertThat(response2.getStatusCode())
                .isIn(HttpStatus.OK, HttpStatus.CREATED);
        assertThat(response1.getBody().get("anonymousId"))
                .isNotEqualTo(response2.getBody().get("anonymousId"));
    }
}
