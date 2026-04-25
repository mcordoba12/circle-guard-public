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
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QrValidationService - QR token validation and health status checking.
 * Tests token validation with health status checks via Redis and JWT signature verification.
 *
 * Uses Mockito to mock StringRedisTemplate for Redis health status lookups.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Gate Service QR Validation Tests")
class GateServiceTest {

    private QrValidationService qrValidationService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final String QR_SECRET = "eyJhbGciOiJIUzI1NiJ9eyJqdGkiOiJjMzFvdmNHSmtiMjVsYTJoNWRHVmZaRkJ5YjNSdmMybHRiMjUwWVcxd2JHVXVZMjl0In0";
    private static final String STATUS_KEY_PREFIX = "user:status:";

    @BeforeEach
    void setUp() {
        qrValidationService = new QrValidationService(redisTemplate);
        ReflectionTestUtils.setField(qrValidationService, "qrSecret", QR_SECRET);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("validateToken with valid token and ACTIVE status returns GREEN validation")
    void test_validateToken_WithValidQr_ReturnsAllowed() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        String token = generateValidToken(anonymousId);

        when(valueOperations.get(STATUS_KEY_PREFIX + anonymousId)).thenReturn("ACTIVE");

        // Act
        QrValidationService.ValidationResult result = qrValidationService.validateToken(token);

        // Assert
        assertNotNull(result, "ValidationResult should not be null");
        assertTrue(result.valid(), "Token with ACTIVE status should be valid");
        assertEquals("GREEN", result.status(), "Status should be GREEN for allowed access");
        assertEquals("Welcome to Campus", result.message(), "Message should be welcome message");
    }

    @Test
    @DisplayName("validateToken with CONTAGIED status returns RED denial")
    void test_validateToken_WithContagiedStatus_ReturnsDenied() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        String token = generateValidToken(anonymousId);

        when(valueOperations.get(STATUS_KEY_PREFIX + anonymousId)).thenReturn("CONTAGIED");

        // Act
        QrValidationService.ValidationResult result = qrValidationService.validateToken(token);

        // Assert
        assertNotNull(result, "ValidationResult should not be null");
        assertFalse(result.valid(), "Token with CONTAGIED status should be invalid");
        assertEquals("RED", result.status(), "Status should be RED for denied access");
        assertTrue(result.message().contains("Access Denied"), "Message should indicate access denied");
    }

    @Test
    @DisplayName("validateToken with POTENTIAL status returns RED denial")
    void test_validateToken_WithPotentialStatus_ReturnsDenied() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        String token = generateValidToken(anonymousId);

        when(valueOperations.get(STATUS_KEY_PREFIX + anonymousId)).thenReturn("POTENTIAL");

        // Act
        QrValidationService.ValidationResult result = qrValidationService.validateToken(token);

        // Assert
        assertNotNull(result, "ValidationResult should not be null");
        assertFalse(result.valid(), "Token with POTENTIAL status should be invalid");
        assertEquals("RED", result.status(), "Status should be RED for denied access");
        assertTrue(result.message().contains("Access Denied"), "Message should indicate access denied");
    }

    @Test
    @DisplayName("validateToken with invalid token signature returns RED error")
    void test_validateToken_WithInvalidSignature_ReturnsError() {
        // Arrange
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.signature";

        // Act
        QrValidationService.ValidationResult result = qrValidationService.validateToken(invalidToken);

        // Assert
        assertNotNull(result, "ValidationResult should not be null");
        assertFalse(result.valid(), "Invalid token should return false");
        assertEquals("RED", result.status(), "Status should be RED for error");
        assertTrue(result.message().contains("Invalid"), "Message should indicate token is invalid");
    }

    @Test
    @DisplayName("validateToken with expired token returns RED error")
    void test_validateToken_WithExpiredToken_ReturnsError() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        Key key = Keys.hmacShaKeyFor(QR_SECRET.getBytes());

        // Create token that expired 1 hour ago
        String expiredToken = Jwts.builder()
                .setSubject(anonymousId.toString())
                .setIssuedAt(new Date(System.currentTimeMillis() - 7200000L))
                .setExpiration(new Date(System.currentTimeMillis() - 3600000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        // Act
        QrValidationService.ValidationResult result = qrValidationService.validateToken(expiredToken);

        // Assert
        assertNotNull(result, "ValidationResult should not be null");
        assertFalse(result.valid(), "Expired token should return false");
        assertEquals("RED", result.status(), "Status should be RED for error");
    }

    @Test
    @DisplayName("validateToken checks Redis with correct key format (user:status:anonymousId)")
    void test_validateToken_ChecksRedisWithCorrectKey() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        String token = generateValidToken(anonymousId);
        String expectedRedisKey = STATUS_KEY_PREFIX + anonymousId;

        when(valueOperations.get(expectedRedisKey)).thenReturn("ACTIVE");

        // Act
        qrValidationService.validateToken(token);

        // Assert
        verify(valueOperations, times(1)).get(expectedRedisKey);
    }

    @Test
    @DisplayName("validateToken with null status in Redis allows access (ACTIVE assumed)")
    void test_validateToken_WithNullStatusInRedis_AllowsAccess() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        String token = generateValidToken(anonymousId);

        when(valueOperations.get(STATUS_KEY_PREFIX + anonymousId)).thenReturn(null);

        // Act
        QrValidationService.ValidationResult result = qrValidationService.validateToken(token);

        // Assert
        assertTrue(result.valid(), "Token with null status (no record) should be valid");
        assertEquals("GREEN", result.status(), "Status should be GREEN when status is not CONTAGIED/POTENTIAL");
    }

    /**
     * Helper method to generate a valid JWT token for testing
     */
    private String generateValidToken(UUID anonymousId) {
        Key key = Keys.hmacShaKeyFor(QR_SECRET.getBytes());
        return Jwts.builder()
                .setSubject(anonymousId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
