package com.circleguard.gateway.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

/**
 * Test suite para QrValidationService.
 * Valida que:
 * - Tokens QR válidos presentes en Redis permitir acceso
 * - Tokens expirados o no en Redis denegar acceso
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QrValidationService Tests")
public class QrValidationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private QrValidationService service;
    private final String secret = "my-super-secret-test-key-32-chars-long";

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new QrValidationService(redisTemplate);
        ReflectionTestUtils.setField(service, "qrSecret", secret);
    }

    @Test
    void shouldValidateCorrectTokenAndAllowAccess() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        when(valueOps.get("user:status:" + anonymousId)).thenReturn("CLEAR");

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
    }

    @Test
    void shouldDenyAccessForContagiedUser() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        when(valueOps.get("user:status:" + anonymousId)).thenReturn("CONTAGIED");

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }

    /**
     * Test: validQrTokenInRedisReturnsTrue
     * Valida que cuando un token QR válido está presente en Redis:
     * - Acceso es permitido (valid = true)
     * - Status es GREEN
     * - Mensaje de bienvenida aparece
     */
    @Test
    @DisplayName("Token QR válido en Redis debe retornar acceso permitido (true)")
    void test_validQrTokenInRedisReturnsTrue() {
        // Arrange
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());

        String validToken = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        // Mock Redis: usuario tiene status ACTIVE (está permitido)
        when(valueOps.get("user:status:" + anonymousId)).thenReturn("ACTIVE");

        // Act
        QrValidationService.ValidationResult result = service.validateToken(validToken);

        // Assert
        // 1. Verifica que acceso es válido
        assertTrue(result.valid(), "Token presente en Redis debe permitir acceso");

        // 2. Verifica que el status es GREEN
        assertEquals("GREEN", result.status(), "Status debe ser GREEN para acceso permitido");

        // 3. Verifica que contiene mensaje de bienvenida
        assertNotNull(result.message(), "Debe contener mensaje");
        assertEquals("Welcome to Campus", result.message(), "Debe mostrar mensaje de bienvenida");
    }

    /**
     * Test: potentialRiskUserDeniesAccess
     * Valida que usuarios con estado POTENTIAL no pueden entrar
     */
    @Test
    @DisplayName("Usuario con estado POTENTIAL debe ser denegado acceso")
    void test_potentialRiskUserDeniesAccess() {
        // Arrange
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());

        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        // Mock Redis: usuario en estado POTENTIAL (riesgo)
        when(valueOps.get("user:status:" + anonymousId)).thenReturn("POTENTIAL");

        // Act
        QrValidationService.ValidationResult result = service.validateToken(token);

        // Assert
        assertFalse(result.valid(), "Usuario POTENTIAL debe ser denegado");
        assertEquals("RED", result.status(), "Status debe ser RED");
    }
}
