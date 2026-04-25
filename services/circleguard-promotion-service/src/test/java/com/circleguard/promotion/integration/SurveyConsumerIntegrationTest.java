package com.circleguard.promotion.integration;

import com.circleguard.promotion.listener.SurveyListener;
import com.circleguard.promotion.service.HealthStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Promotion Service Kafka consumer.
 * Validates that the SurveyListener correctly consumes survey.submitted events
 * from Kafka and triggers appropriate status updates via HealthStatusService.
 *
 * Uses @EmbeddedKafka to publish test events and verify consumption.
 * Mocks HealthStatusService to verify it's called with correct parameters.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:29094",
                "port=29094"
        },
        topics = {"survey.submitted", "certificate.validated"}
)
@ActiveProfiles("test")
@DisplayName("Survey Listener Kafka Consumer Integration Tests")
class SurveyConsumerIntegrationTest {

    @Autowired
    private SurveyListener surveyListener;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockBean
    private HealthStatusService healthStatusService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private UUID anonymousId;
    private Map<String, Object> surveyEvent;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        anonymousId = UUID.randomUUID();

        // Create survey.submitted event payload
        surveyEvent = new HashMap<>();
        surveyEvent.put("anonymousId", anonymousId);
        surveyEvent.put("hasSymptoms", true);
        surveyEvent.put("timestamp", System.currentTimeMillis());

        // Setup mocks
        lenient().doNothing().when(healthStatusService)
                .updateStatus(anyString(), anyString());
    }

    @Test
    @DisplayName("SurveyListener consumes survey.submitted event with symptoms and calls updateStatus")
    void test_surveyListener_ConsumesEventAndCallsUpdateStatus() throws InterruptedException {
        // Arrange
        surveyEvent.put("hasSymptoms", true);

        // Act
        kafkaTemplate.send("survey.submitted", anonymousId.toString(), toJson(surveyEvent));

        // Wait for consumer to process
        Thread.sleep(1000);

        // Assert
        verify(healthStatusService, timeout(5000).atLeastOnce())
                .updateStatus(anonymousId.toString(), "SUSPECT");
    }

    @Test
    @DisplayName("SurveyListener does not update status when hasSymptoms is false")
    void test_surveyListener_SkipsUpdateWhenNoSymptoms() throws InterruptedException {
        // Arrange
        surveyEvent.put("hasSymptoms", false);

        // Act
        kafkaTemplate.send("survey.submitted", anonymousId.toString(), toJson(surveyEvent));

        // Wait for consumer to process
        Thread.sleep(1000);

        // Assert
        verify(healthStatusService, times(0)).updateStatus(anyString(), eq("SUSPECT"));
    }

    @Test
    @DisplayName("SurveyListener extracts anonymousId from event correctly")
    void test_surveyListener_ExtractsAnonymousIdCorrectly() throws InterruptedException {
        // Arrange
        UUID expectedId = UUID.randomUUID();
        surveyEvent.put("anonymousId", expectedId);
        surveyEvent.put("hasSymptoms", true);

        // Act
        kafkaTemplate.send("survey.submitted", expectedId.toString(), toJson(surveyEvent));

        // Wait for consumer to process
        Thread.sleep(1000);

        // Assert
        verify(healthStatusService, timeout(5000))
                .updateStatus(expectedId.toString(), "SUSPECT");
    }

    @Test
    @DisplayName("SurveyListener handles multiple survey.submitted events in sequence")
    void test_surveyListener_HandlesMultipleEvents() throws InterruptedException {
        // Arrange
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Map<String, Object> event1 = new HashMap<>();
        event1.put("anonymousId", id1);
        event1.put("hasSymptoms", true);
        event1.put("timestamp", System.currentTimeMillis());

        Map<String, Object> event2 = new HashMap<>();
        event2.put("anonymousId", id2);
        event2.put("hasSymptoms", true);
        event2.put("timestamp", System.currentTimeMillis());

        // Act
        kafkaTemplate.send("survey.submitted", id1.toString(), toJson(event1));
        kafkaTemplate.send("survey.submitted", id2.toString(), toJson(event2));

        // Wait for consumer to process
        Thread.sleep(2000);

        // Assert
        verify(healthStatusService, timeout(5000)).updateStatus(id1.toString(), "SUSPECT");
        verify(healthStatusService, timeout(5000)).updateStatus(id2.toString(), "SUSPECT");
    }

    @Test
    @DisplayName("SurveyListener includes hasSymptoms flag in event payload")
    void test_surveyListener_IncludesHasSymptomsInPayload() throws InterruptedException {
        // Arrange
        surveyEvent.put("hasSymptoms", true);
        boolean hasSymptomsFlag = (boolean) surveyEvent.get("hasSymptoms");

        // Act
        kafkaTemplate.send("survey.submitted", anonymousId.toString(), toJson(surveyEvent));

        // Wait for consumer to process
        Thread.sleep(1000);

        // Assert
        assertThat(hasSymptomsFlag).isTrue();
        verify(healthStatusService, timeout(5000))
                .updateStatus(anonymousId.toString(), "SUSPECT");
    }

    @Test
    @DisplayName("SurveyListener handles events from survey.submitted topic correctly")
    void test_surveyListener_ListensToCorrectTopic() throws InterruptedException {
        // Arrange
        final String TOPIC = "survey.submitted";

        // Act
        kafkaTemplate.send(TOPIC, anonymousId.toString(), toJson(surveyEvent));

        // Wait for consumer to process
        Thread.sleep(1000);

        // Assert
        verify(healthStatusService, timeout(5000).atLeastOnce())
                .updateStatus(anyString(), anyString());
    }

    @Test
    @DisplayName("SurveyListener correctly maps event message key to anonymousId")
    void test_surveyListener_MapsMessageKeyToAnonymousId() throws InterruptedException {
        // Arrange
        String messageKey = anonymousId.toString();
        surveyEvent.put("anonymousId", anonymousId);
        surveyEvent.put("hasSymptoms", true);

        // Act
        kafkaTemplate.send("survey.submitted", messageKey, toJson(surveyEvent));

        // Wait for consumer to process
        Thread.sleep(1000);

        // Assert
        verify(healthStatusService, timeout(5000))
                .updateStatus(messageKey, "SUSPECT");
    }

    @Test
    @DisplayName("SurveyListener handles event with timestamp metadata")
    void test_surveyListener_HandlesEventWithTimestamp() throws InterruptedException {
        // Arrange
        long eventTimestamp = System.currentTimeMillis();
        surveyEvent.put("timestamp", eventTimestamp);
        surveyEvent.put("hasSymptoms", true);

        // Act
        kafkaTemplate.send("survey.submitted", anonymousId.toString(), toJson(surveyEvent));

        // Wait for consumer to process
        Thread.sleep(1000);

        // Assert
        assertThat(surveyEvent.get("timestamp")).isEqualTo(eventTimestamp);
        verify(healthStatusService, timeout(5000))
                .updateStatus(anonymousId.toString(), "SUSPECT");
    }

    /**
     * Helper method to convert event map to JSON string
     */
    private String toJson(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event to JSON", e);
        }
    }
}
