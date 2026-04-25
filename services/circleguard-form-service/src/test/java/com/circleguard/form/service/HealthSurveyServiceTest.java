package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HealthSurveyService - Health survey submission and validation.
 * Tests survey submission with symptom detection, Kafka event publishing, and certificate validation.
 *
 * Uses Mockito to mock repositories, questionnaire service, and Kafka template.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Health Survey Service Tests")
class HealthSurveyServiceTest {

    private HealthSurveyService healthSurveyService;

    @Mock
    private HealthSurveyRepository healthSurveyRepository;

    @Mock
    private QuestionnaireService questionnaireService;

    @Mock
    private SymptomMapper symptomMapper;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Captor
    private ArgumentCaptor<String> topicCaptor;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    @BeforeEach
    void setUp() {
        healthSurveyService = new HealthSurveyService(
                healthSurveyRepository,
                questionnaireService,
                symptomMapper,
                kafkaTemplate
        );
    }

    @Test
    @DisplayName("submitSurvey publishes survey.submitted Kafka event with hasSymptoms status")
    void test_submitSurvey_PublishesKafkaEvent() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(anonymousId)
                .hasFever(null)
                .hasCough(null)
                .build();

        Questionnaire questionnaire = Questionnaire.builder()
                .id(UUID.randomUUID())
                .version(1)
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(survey, questionnaire)).thenReturn(true);
        when(healthSurveyRepository.save(any(HealthSurvey.class))).thenReturn(survey);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

        // Act
        HealthSurvey result = healthSurveyService.submitSurvey(survey);

        // Assert
        assertNotNull(result, "Survey should be saved and returned");
        verify(kafkaTemplate, times(1)).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());
        assertEquals("survey.submitted", topicCaptor.getValue(), "Event should be sent to survey.submitted topic");
        assertEquals(anonymousId.toString(), keyCaptor.getValue(), "Event key should be anonymousId");
        assertInstanceOf(Map.class, eventCaptor.getValue(), "Event payload should be a Map");
    }

    @Test
    @DisplayName("submitSurvey with detected symptoms sets hasFever and hasCough flags to true")
    void test_submitSurvey_WithSymptoms_SetsFeverCoughFlags() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(anonymousId)
                .hasFever(null)
                .hasCough(null)
                .build();

        Questionnaire questionnaire = Questionnaire.builder().id(UUID.randomUUID()).build();
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(survey, questionnaire)).thenReturn(true);
        when(healthSurveyRepository.save(any(HealthSurvey.class))).thenAnswer(invocation -> {
            HealthSurvey savedSurvey = invocation.getArgument(0);
            return savedSurvey;
        });

        // Act
        HealthSurvey result = healthSurveyService.submitSurvey(survey);

        // Assert
        assertNotNull(result, "Survey should be returned");
        assertTrue(result.getHasFever(), "hasFever should be set to true when symptoms detected");
        assertTrue(result.getHasCough(), "hasCough should be set to true when symptoms detected");
    }

    @Test
    @DisplayName("submitSurvey with attachment sets validation status to PENDING")
    void test_submitSurvey_WithAttachment_SetsValidationStatusPending() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        String attachmentPath = "/uploads/certificate.pdf";
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(anonymousId)
                .attachmentPath(attachmentPath)
                .build();

        Questionnaire questionnaire = Questionnaire.builder().id(UUID.randomUUID()).build();
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(survey, questionnaire)).thenReturn(false);
        when(healthSurveyRepository.save(any(HealthSurvey.class))).thenAnswer(invocation -> {
            HealthSurvey savedSurvey = invocation.getArgument(0);
            return savedSurvey;
        });

        // Act
        HealthSurvey result = healthSurveyService.submitSurvey(survey);

        // Assert
        assertNotNull(result, "Survey should be returned");
        assertEquals(ValidationStatus.PENDING, result.getValidationStatus(),
                "Validation status should be set to PENDING when attachment is present");
    }

    @Test
    @DisplayName("submitSurvey publishes event with hasSymptoms=false when no symptoms detected")
    void test_submitSurvey_WithoutSymptoms_PublishesHasSymptomsFalse() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder().anonymousId(anonymousId).build();

        Questionnaire questionnaire = Questionnaire.builder().id(UUID.randomUUID()).build();
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(survey, questionnaire)).thenReturn(false);
        when(healthSurveyRepository.save(any(HealthSurvey.class))).thenReturn(survey);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

        // Act
        healthSurveyService.submitSurvey(survey);

        // Assert
        verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) eventCaptor.getValue();
        assertFalse((Boolean) event.get("hasSymptoms"), "Event should contain hasSymptoms=false");
    }

    @Test
    @DisplayName("validateSurvey with APPROVED status publishes certificate.validated Kafka event")
    void test_validateSurvey_WithApprovedStatus_PublishesEvent() {
        // Arrange
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID anonymousId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(anonymousId)
                .validationStatus(ValidationStatus.PENDING)
                .build();

        when(healthSurveyRepository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(healthSurveyRepository.save(any(HealthSurvey.class))).thenReturn(survey);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

        // Act
        healthSurveyService.validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);

        // Assert
        verify(kafkaTemplate, times(1)).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());
        assertEquals("certificate.validated", topicCaptor.getValue(),
                "Event should be sent to certificate.validated topic");
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) eventCaptor.getValue();
        assertEquals("APPROVED", event.get("status"), "Event status should be APPROVED");
        assertEquals(adminId, event.get("adminId"), "Event should include adminId");
    }

    @Test
    @DisplayName("validateSurvey with REJECTED status does not publish Kafka event")
    void test_validateSurvey_WithRejectedStatus_NoEvent() {
        // Arrange
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(UUID.randomUUID())
                .validationStatus(ValidationStatus.PENDING)
                .build();

        when(healthSurveyRepository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(healthSurveyRepository.save(any(HealthSurvey.class))).thenReturn(survey);

        // Act
        healthSurveyService.validateSurvey(surveyId, ValidationStatus.REJECTED, adminId);

        // Assert
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("validateSurvey with nonexistent survey throws RuntimeException")
    void test_validateSurvey_WithNonexistentSurvey_ThrowsException() {
        // Arrange
        UUID surveyId = UUID.randomUUID();
        when(healthSurveyRepository.findById(surveyId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> healthSurveyService.validateSurvey(surveyId, ValidationStatus.APPROVED, UUID.randomUUID()),
                "Should throw RuntimeException when survey not found"
        );
        assertTrue(exception.getMessage().contains("Survey not found"),
                "Exception message should indicate survey was not found");
    }
}
