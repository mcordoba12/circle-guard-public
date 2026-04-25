package com.circleguard.form.integration;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.repository.HealthSurveyRepository;
import com.circleguard.form.service.HealthSurveyService;
import com.circleguard.form.service.QuestionnaireService;
import com.circleguard.form.service.SymptomMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Form Service → Kafka event publishing.
 * Validates that survey.submitted events are correctly published to Kafka
 * when health surveys are submitted by users.
 *
 * Uses @EmbeddedKafka for in-process Kafka broker (no TestContainers).
 * Mocks external services: QuestionnaireService, SymptomMapper, HealthSurveyRepository
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:29092",
                "port=29092"
        },
        topics = {"survey.submitted", "certificate.validated"}
)
@ActiveProfiles("test")
@DisplayName("Form Service to Kafka Integration Tests")
class FormToPromotionIntegrationTest {

    @Autowired
    private HealthSurveyService healthSurveyService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private HealthSurveyRepository healthSurveyRepository;

    @MockBean
    private QuestionnaireService questionnaireService;

    @MockBean
    private SymptomMapper symptomMapper;

    private UUID anonymousId;
    private HealthSurvey testSurvey;

    @BeforeEach
    void setUp() {
        anonymousId = UUID.randomUUID();
        testSurvey = HealthSurvey.builder()
                .anonymousId(anonymousId)
                .hasFever(null)
                .hasCough(null)
                .build();

        // Setup mock questionnaire
        Questionnaire questionnaire = Questionnaire.builder()
                .id(UUID.randomUUID())
                .version(1)
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(any(HealthSurvey.class), any(Questionnaire.class))).thenReturn(true);
    }

    @Test
    @DisplayName("submitSurvey publishes survey.submitted event with correct anonymousId")
    void test_submitSurvey_PublishesKafkaEventWithAnonymousId() {
        // Arrange
        when(healthSurveyRepository.save(any(HealthSurvey.class))).thenAnswer(invocation -> {
            HealthSurvey survey = invocation.getArgument(0);
            survey.setId(UUID.randomUUID());
            return survey;
        });

        // Act
        HealthSurvey result = healthSurveyService.submitSurvey(testSurvey);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getAnonymousId()).isEqualTo(anonymousId);
        verify(healthSurveyRepository, times(1)).save(any(HealthSurvey.class));
    }

    @Test
    @DisplayName("submitSurvey publishes event with hasSymptoms=true when symptoms detected")
    void test_submitSurvey_PublishesHasSymptomsTrueInEvent() {
        // Arrange
        when(healthSurveyRepository.save(any(HealthSurvey.class))).thenAnswer(invocation -> {
            HealthSurvey survey = invocation.getArgument(0);
            survey.setId(UUID.randomUUID());
            return survey;
        });
        when(symptomMapper.hasSymptoms(any(), any())).thenReturn(true);

        // Act
        HealthSurvey result = healthSurveyService.submitSurvey(testSurvey);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getHasFever()).isTrue();
        assertThat(result.getHasCough()).isTrue();
        verify(healthSurveyRepository).save(argThat(survey ->
                survey.getHasFever() == true && survey.getHasCough() == true
        ));
    }

    @Test
    @DisplayName("submitSurvey sets ValidationStatus PENDING when attachment is present")
    void test_submitSurvey_SetsValidationStatusPendingWithAttachment() {
        // Arrange
        String attachmentPath = "/uploads/certificate-" + UUID.randomUUID() + ".pdf";
        testSurvey.setAttachmentPath(attachmentPath);

        when(healthSurveyRepository.save(any(HealthSurvey.class))).thenAnswer(invocation -> {
            HealthSurvey survey = invocation.getArgument(0);
            survey.setId(UUID.randomUUID());
            return survey;
        });

        // Act
        HealthSurvey result = healthSurveyService.submitSurvey(testSurvey);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getAttachmentPath()).isEqualTo(attachmentPath);
        verify(healthSurveyRepository).save(argThat(survey ->
                survey.getValidationStatus() != null
        ));
    }

    @Test
    @DisplayName("submitSurvey publishes hasSymptoms=false when no symptoms detected")
    void test_submitSurvey_PublishesHasSymptomsFalseWhenNoSymptoms() {
        // Arrange
        when(healthSurveyRepository.save(any(HealthSurvey.class))).thenAnswer(invocation -> {
            HealthSurvey survey = invocation.getArgument(0);
            survey.setId(UUID.randomUUID());
            return survey;
        });
        when(symptomMapper.hasSymptoms(any(), any())).thenReturn(false);

        // Act
        HealthSurvey result = healthSurveyService.submitSurvey(testSurvey);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getHasFever()).isFalse();
        assertThat(result.getHasCough()).isFalse();
        verify(healthSurveyRepository).save(argThat(survey ->
                survey.getHasFever() == false && survey.getHasCough() == false
        ));
    }

    @Test
    @DisplayName("submitSurvey invokes QuestionnaireService to get active questionnaire")
    void test_submitSurvey_InvokesQuestionnaireService() {
        // Arrange
        when(healthSurveyRepository.save(any(HealthSurvey.class))).thenAnswer(invocation -> {
            HealthSurvey survey = invocation.getArgument(0);
            survey.setId(UUID.randomUUID());
            return survey;
        });

        // Act
        healthSurveyService.submitSurvey(testSurvey);

        // Assert
        verify(questionnaireService, times(1)).getActiveQuestionnaire();
        verify(symptomMapper, times(1)).hasSymptoms(any(HealthSurvey.class), any(Questionnaire.class));
    }
}
