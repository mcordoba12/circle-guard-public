package com.circleguard.gateway.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.circleguard.gateway.service.QrValidationService;
import com.circleguard.gateway.service.QrValidationService.ValidationResult;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Gateway Service Redis status lookups.
 * Validates that QR token validation correctly checks Redis for user health status
 * and returns appropriate access decisions (GREEN/RED).
 *
 * Uses @MockBean for StringRedisTemplate (no embedded Redis needed).
 * Tests the complete validation flow: token parsing → Redis lookup → access decision
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Gateway Service Redis Integration Tests")
class GatewayRedisIntegrationTest {

    @Autowired
    private QrValidationService qrValidationService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    private static final String QR_SECRET = "eyJhbGciOiJIUzI1NiJ9eyJqdGkiOiJjMzFvdmNHSmtiMjVsYTJoNWRHVmZaRkJ5YjNSdmMybHRiMjUwWVcxd2JHVXVZMjl0In0";
    private static final String STATUS_KEY_PREFIX = "user:status:";

    @BeforeEach
    void setUp() {
        // Inject QR secret via reflection
        ReflectionTestUtils.setField(qrValidationService, "qrSecret", QR_SECRET);

        // Setup Redis template mock
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("validateToken with valid QR and ACTIVE status returns GREEN access")
    void test_validateToken_ValidQrWithActiveStatus_ReturnsGreenAccess() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        String token = generateValidToken(anonymousId);
        String statusKey = STATUS_KEY_PREFIX + anonymousId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(statusKey)).thenReturn("ACTIVE");

        // Act
        ValidationResult result = qrValidationService.validateToken(token);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.valid()).isTrue();
        assertThat(result.status()).isEqualTo("GREEN");
        assertThat(result.message()).contains("Welcome");
    }

    @Test
    @DisplayName("validateToken with CONTAGIED status returns RED denial")
    void test_validateToken_ContagiedStatus_ReturnsRedDenial() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        String token = generateValidToken(anonymousId);
        String statusKey = STATUS_KEY_PREFIX + anonymousId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(statusKey)).thenReturn("CONTAGIED");

        // Act
        ValidationResult result = qrValidationService.validateToken(token);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.valid()).isFalse();
        assertThat(result.status()).isEqualTo("RED");
        assertThat(result.message()).contains("Access Denied");
    }

    @Test
    @DisplayName("validateToken with POTENTIAL status returns RED denial")
    void test_validateToken_PotentialStatus_ReturnsRedDenial() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        String token = generateValidToken(anonymousId);
        String statusKey = STATUS_KEY_PREFIX + anonymousId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(statusKey)).thenReturn("POTENTIAL");

        // Act
        ValidationResult result = qrValidationService.validateToken(token);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.valid()).isFalse();
        assertThat(result.status()).isEqualTo("RED");
        assertThat(result.message()).contains("Access Denied");
    }

    @Test
    @DisplayName("validateToken queries Redis with correct key format")
    void test_validateToken_QueriesRedisWithCorrectKeyFormat() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        String token = generateValidToken(anonymousId);
        String expectedKey = STATUS_KEY_PREFIX + anonymousId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn("ACTIVE");

        // Act
        qrValidationService.validateToken(token);

        // Assert
        verify(valueOperations, times(1)).get(expectedKey);
    }

    @Test
    @DisplayName("validateToken with null Redis status assumes ACTIVE (GREEN)")
    void test_validateToken_NullRedisStatus_ReturnsGreen() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        String token = generateValidToken(anonymousId);
        String statusKey = STATUS_KEY_PREFIX + anonymousId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(statusKey)).thenReturn(null);

        // Act
        ValidationResult result = qrValidationService.validateToken(token);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.valid()).isTrue();
        assertThat(result.status()).isEqualTo("GREEN");
    }

    @Test
    @DisplayName("validateToken with SUSPECT status allows access (GREEN)")
    void test_validateToken_SuspectStatus_AllowsAccess() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        String token = generateValidToken(anonymousId);
        String statusKey = STATUS_KEY_PREFIX + anonymousId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(statusKey)).thenReturn("SUSPECT");

        // Act
        ValidationResult result = qrValidationService.validateToken(token);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.valid()).isTrue();
        assertThat(result.status()).isEqualTo("GREEN");
    }

    @Test
    @DisplayName("validateToken with PROBABLE status allows access (GREEN)")
    void test_validateToken_ProbableStatus_AllowsAccess() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        String token = generateValidToken(anonymousId);
        String statusKey = STATUS_KEY_PREFIX + anonymousId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(statusKey)).thenReturn("PROBABLE");

        // Act
        ValidationResult result = qrValidationService.validateToken(token);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.valid()).isTrue();
        assertThat(result.status()).isEqualTo("GREEN");
    }

    @Test
    @DisplayName("validateToken with invalid token signature returns RED error")
    void test_validateToken_InvalidTokenSignature_ReturnsRedError() {
        // Arrange
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.signature";

        // Act
        ValidationResult result = qrValidationService.validateToken(invalidToken);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.valid()).isFalse();
        assertThat(result.status()).isEqualTo("RED");
        assertThat(result.message()).contains("Invalid");
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
