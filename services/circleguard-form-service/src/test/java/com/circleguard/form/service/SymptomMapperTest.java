package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite para SymptomMapper.
 * Valida que:
 * - Los síntomas críticos (fiebre + dificultad respiratoria) se detecten correctamente
 * - Los síntomas leves (solo dolor de cabeza) se detecten como no críticos
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SymptomMapper Tests")
class SymptomMapperTest {

    private SymptomMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SymptomMapper();
    }

    @Test
    void shouldDetectSymptomsFromFever() {
        UUID questionId = UUID.randomUUID();
        Question q = Question.builder()
                .id(questionId)
                .text("Do you have a fever?")
                .type(QuestionType.YES_NO)
                .build();

        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(q))
                .build();

        HealthSurvey survey = HealthSurvey.builder()
                .responses(Map.of(questionId.toString(), "YES"))
                .build();

        assertTrue(mapper.hasSymptoms(survey, questionnaire));
    }

    @Test
    void shouldNotDetectSymptomsWhenNo() {
        UUID questionId = UUID.randomUUID();
        Question q = Question.builder()
                .id(questionId)
                .text("Do you have a fever?")
                .type(QuestionType.YES_NO)
                .build();

        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(q))
                .build();

        HealthSurvey survey = HealthSurvey.builder()
                .responses(Map.of(questionId.toString(), "NO"))
                .build();

        assertFalse(mapper.hasSymptoms(survey, questionnaire));
    }

    /**
     * Test: criticalSymptomsMapToHighRisk
     * Valida que cuando un usuario reporta síntomas críticos:
     * - Fiebre: YES
     * - Dificultad para respirar: YES
     * - El resultado es: síntomas detectados (high risk)
     */
    @Test
    @DisplayName("Síntomas críticos (fiebre + dificultad respiratoria) deben detectarse como HIGH RISK")
    void test_criticalSymptomsMapToHighRisk() {
        // Arrange: crear preguntas críticas
        UUID feverId = UUID.randomUUID();
        UUID breathingId = UUID.randomUUID();

        Question feverQuestion = Question.builder()
                .id(feverId)
                .text("Do you have a fever?")
                .type(QuestionType.YES_NO)
                .build();

        Question breathingQuestion = Question.builder()
                .id(breathingId)
                .text("Do you have difficulty breathing?")
                .type(QuestionType.YES_NO)
                .build();

        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(feverQuestion, breathingQuestion))
                .build();

        // Respuestas: ambas preguntas en YES
        HealthSurvey survey = HealthSurvey.builder()
                .responses(Map.of(
                        feverId.toString(), "YES",
                        breathingId.toString(), "YES"
                ))
                .build();

        // Act
        boolean hasCriticalSymptoms = mapper.hasSymptoms(survey, questionnaire);

        // Assert
        // 1. Verifica que se detectaron síntomas críticos
        assertTrue(hasCriticalSymptoms, "Debe detectar síntomas críticos (fiebre + dificultad respiratoria)");

        // 2. Verifica que ambas preguntas fueron evaluadas
        assertNotNull(questionnaire.getQuestions(), "Las preguntas no deben ser nulas");
        assertEquals(2, questionnaire.getQuestions().size(), "Debe haber 2 preguntas críticas");

        // 3. Verifica que las respuestas contienen ambos síntomas
        assertTrue(survey.getResponses().containsKey(feverId.toString()) &&
                   "YES".equals(survey.getResponses().get(feverId.toString())),
                "Debe contener respuesta afirmativa para fiebre");
    }

    /**
     * Test: mildSymptomsMapToLowRisk
     * Valida que cuando un usuario reporta solo síntomas leves:
     * - Dolor de cabeza: YES (no es crítico)
     * - Fiebre: NO
     * - Dificultad para respirar: NO
     * - El resultado es: NO síntomas críticos detectados (low risk)
     */
    @Test
    @DisplayName("Síntomas leves (solo dolor de cabeza) deben detectarse como LOW RISK o no críticos")
    void test_mildSymptomsMapToLowRisk() {
        // Arrange: crear preguntas con síntomas leves
        UUID headacheId = UUID.randomUUID();
        UUID feverId = UUID.randomUUID();
        UUID breathingId = UUID.randomUUID();

        Question headacheQuestion = Question.builder()
                .id(headacheId)
                .text("Do you have a headache?")
                .type(QuestionType.YES_NO)
                .build();

        Question feverQuestion = Question.builder()
                .id(feverId)
                .text("Do you have a fever?")
                .type(QuestionType.YES_NO)
                .build();

        Question breathingQuestion = Question.builder()
                .id(breathingId)
                .text("Do you have difficulty breathing?")
                .type(QuestionType.YES_NO)
                .build();

        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(headacheQuestion, feverQuestion, breathingQuestion))
                .build();

        // Respuestas: solo dolor de cabeza, sin síntomas críticos
        HealthSurvey survey = HealthSurvey.builder()
                .responses(Map.of(
                        headacheId.toString(), "YES",
                        feverId.toString(), "NO",
                        breathingId.toString(), "NO"
                ))
                .build();

        // Act
        boolean hasCriticalSymptoms = mapper.hasSymptoms(survey, questionnaire);

        // Assert
        // 1. Verifica que NO se detectaron síntomas críticos
        assertFalse(hasCriticalSymptoms,
                "No debe detectar síntomas críticos para dolor de cabeza aislado");

        // 2. Verifica que los síntomas NO incluyen fiebre ni dificultad respiratoria
        assertEquals("NO", survey.getResponses().get(feverId.toString()),
                "Fiebre debe ser NO");
        assertEquals("NO", survey.getResponses().get(breathingId.toString()),
                "Dificultad respiratoria debe ser NO");

        // 3. Verifica que solo hay dolor de cabeza (síntoma no crítico)
        assertEquals("YES", survey.getResponses().get(headacheId.toString()),
                "Dolor de cabeza debe estar presente pero no es crítico");
    }

    /**
     * Test: nullQuestionnareReturnsFalse
     * Valida que con cuestionario nulo:
     * - No lanza excepciones
     * - Retorna false
     */
    @Test
    @DisplayName("Cuestionario nulo debe retornar false sin excepción")
    void test_nullQuestionnareReturnsFalse() {
        // Arrange
        HealthSurvey survey = HealthSurvey.builder()
                .responses(new HashMap<>())
                .build();

        // Act & Assert
        assertFalse(mapper.hasSymptoms(survey, null),
                "Debe retornar false con cuestionario nulo sin lanzar excepción");
    }

    /**
     * Test: nullResponsesReturnsFalse
     * Valida que con respuestas nulas:
     * - No lanza excepciones
     * - Retorna false
     */
    @Test
    @DisplayName("Respuestas nulas debe retornar false sin excepción")
    void test_nullResponsesReturnsFalse() {
        // Arrange
        UUID questionId = UUID.randomUUID();
        Question q = Question.builder()
                .id(questionId)
                .text("Do you have a fever?")
                .type(QuestionType.YES_NO)
                .build();

        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(q))
                .build();

        HealthSurvey survey = HealthSurvey.builder()
                .responses(null)
                .build();

        // Act & Assert
        assertFalse(mapper.hasSymptoms(survey, questionnaire),
                "Debe retornar false con respuestas nulas sin lanzar excepción");
    }
}
