package com.circleguard.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Key;
import java.util.*;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtTokenService - JWT token generation and validation.
 * Tests token generation with proper claims and expiration settings.
 *
 * Uses Mockito to mock Authentication object and test JWT operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JWT Token Service Tests")
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @Mock
    private Authentication authentication;

    private static final String JWT_SECRET = "eyJhbGciOiJIUzI1NiJ9eyJqdGkiOiJjMzFvdmNHSmtiMjVsYTJoNWRHVmZaRkJ5YjNSdmMybHRiMjUwWVcxd2JHVXVZMjl0In0";
    private static final long JWT_EXPIRATION = 3600000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(JWT_SECRET, JWT_EXPIRATION);
    }

    @Test
    @DisplayName("generateToken with valid authentication returns non-null JWT string")
    void test_generateToken_ReturnsValidJwt() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        Collection<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("READ_HEALTH"),
                new SimpleGrantedAuthority("SUBMIT_SURVEY")
        );
        lenient().doReturn(authorities).when(authentication).getAuthorities();

        // Act
        String token = jwtTokenService.generateToken(anonymousId, authentication);

        // Assert
        assertNotNull(token, "Token should not be null");
        assertTrue(token.length() > 0, "Token should not be empty");
        assertTrue(token.contains("."), "JWT token should have three parts separated by dots");
        assertEquals(3, token.split("\\.").length, "JWT token should have exactly 3 parts (header.payload.signature)");
    }

    @Test
    @DisplayName("Generated token contains correct subject (anonymousId) when decoded")
    void test_generateToken_ContainsCorrectSubject() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("READ_HEALTH"));
        lenient().doReturn(authorities).when(authentication).getAuthorities();

        // Act
        String token = jwtTokenService.generateToken(anonymousId, authentication);

        // Decode token to verify subject
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        String subject = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();

        // Assert
        assertNotNull(subject, "Token subject should not be null");
        assertEquals(anonymousId.toString(), subject, "Token subject should match the provided anonymousId");
    }

    @Test
    @DisplayName("Generated token includes permissions claim from authentication authorities")
    void test_generateToken_IncludesPermissionsClaim() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        Collection<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("READ_HEALTH"),
                new SimpleGrantedAuthority("WRITE_SURVEY")
        );
        lenient().doReturn(authorities).when(authentication).getAuthorities();

        // Act
        String token = jwtTokenService.generateToken(anonymousId, authentication);

        // Decode token to verify permissions claim
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("permissions", List.class);

        // Assert
        assertNotNull(permissions, "Permissions claim should not be null");
        assertEquals(2, permissions.size(), "Permissions list should contain all authorities");
        assertTrue(permissions.contains("READ_HEALTH"), "Permissions should include READ_HEALTH");
        assertTrue(permissions.contains("WRITE_SURVEY"), "Permissions should include WRITE_SURVEY");
    }

    @Test
    @DisplayName("generateToken with empty authorities creates token with empty permissions list")
    void test_generateToken_WithEmptyAuthorities() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        when(authentication.getAuthorities()).thenReturn(List.of());

        // Act
        String token = jwtTokenService.generateToken(anonymousId, authentication);

        // Decode token
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("permissions", List.class);

        // Assert
        assertNotNull(token, "Token should be generated even with no authorities");
        assertNotNull(permissions, "Permissions claim should exist");
        assertTrue(permissions.isEmpty(), "Permissions list should be empty when no authorities provided");
    }

    @Test
    @DisplayName("Generated token has expiration timestamp set in the future")
    void test_generateToken_HasValidExpiration() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        when(authentication.getAuthorities()).thenReturn(List.of());
        long beforeTokenGeneration = System.currentTimeMillis();

        // Act
        String token = jwtTokenService.generateToken(anonymousId, authentication);

        // Decode token to check expiration
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        Date expiration = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();
        long afterTokenGeneration = System.currentTimeMillis();

        // Assert
        assertNotNull(expiration, "Expiration date should be set");
        assertTrue(expiration.getTime() > beforeTokenGeneration, "Token expiration should be in the future");
        long expirationDiff = expiration.getTime() - beforeTokenGeneration;
        assertTrue(expirationDiff >= JWT_EXPIRATION - 1000,
                "Token should expire within configured expiration time. Expected approximately " + JWT_EXPIRATION + "ms but got " + expirationDiff + "ms");
    }
}
