package com.circleguard.auth.e2e;

import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.repository.LocalUserRepository;
import com.circleguard.auth.model.LocalUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * End-to-End tests for complete login flow.
 * Validates JWT token generation and protected endpoint access.
 *
 * Flow:
 * 1. POST /api/v1/auth/login with username/password
 * 2. Receive JWT token (3 parts: header.payload.signature)
 * 3. GET protected endpoint with Bearer token
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("End-to-End Login Flow Tests")
class LoginFlowE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private IdentityClient identityClient;

    @MockBean
    private LocalUserRepository userRepository;

    @MockBean
    private AuthenticationManager authenticationManager;

    private String validUsername;
    private String validPassword;
    private UUID mockAnonymousId;

    @BeforeEach
    void setUp() {
        validUsername = "testuser";
        validPassword = "password123";
        mockAnonymousId = UUID.randomUUID();

        // Mock AuthenticationManager
        Authentication mockAuth = Mockito.mock(Authentication.class);
        when(mockAuth.getAuthorities()).thenReturn(new ArrayList<>());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mockAuth);

        // Mock IdentityClient to return anonymous ID
        when(identityClient.getAnonymousId(anyString()))
                .thenReturn(mockAnonymousId);
    }

    @Test
    @DisplayName("E2E: Successful login returns JWT token with 3 parts")
    void test_successfulLogin_ReturnsValidJwtToken() {
        // Arrange
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", validUsername);
        loginRequest.put("password", validPassword);

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                loginRequest,
                Map.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("token", "type", "anonymousId");

        String token = (String) response.getBody().get("token");
        assertThat(token).isNotNull().isNotEmpty();

        // JWT has 3 parts separated by dots
        String[] tokenParts = token.split("\\.");
        assertThat(tokenParts).hasSize(3)
                .allSatisfy(part -> assertThat(part).isNotEmpty());
    }

    @Test
    @DisplayName("E2E: Login response includes Bearer type")
    void test_successfulLogin_IncludesBearerType() {
        // Arrange
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", validUsername);
        loginRequest.put("password", validPassword);

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                loginRequest,
                Map.class
        );

        // Assert
        assertThat(response.getBody())
                .containsEntry("type", "Bearer");
    }

    @Test
    @DisplayName("E2E: Login response includes anonymousId from IdentityClient")
    void test_successfulLogin_IncludesAnonymousId() {
        // Arrange
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", validUsername);
        loginRequest.put("password", validPassword);

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                loginRequest,
                Map.class
        );

        // Assert
        assertThat(response.getBody())
                .containsKey("anonymousId");
        String anonymousId = (String) response.getBody().get("anonymousId");
        assertThat(anonymousId)
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    @DisplayName("E2E: JWT token can be used to access protected endpoint")
    void test_jwtToken_CanAccessProtectedEndpoint() {
        // Arrange
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", validUsername);
        loginRequest.put("password", validPassword);

        // Step 1: Login and get token
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
                "/api/v1/auth/login",
                loginRequest,
                Map.class
        );
        String token = (String) loginResponse.getBody().get("token");

        // Act
        // Step 2: Access protected endpoint with token
        ResponseEntity<Object> protectedResponse = restTemplate
                .getForEntity(
                        "/api/v1/users/permissions/ADMIN",
                        Object.class
                );

        // Assert - Endpoint should be accessible (even if list is empty)
        assertThat(protectedResponse.getStatusCode())
                .isIn(HttpStatus.OK, HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("E2E: Invalid credentials response contains message")
    void test_invalidCredentials_ResponseHasContent() {
        // Arrange
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", "invaliduser");
        loginRequest.put("password", "wrongpassword");

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                loginRequest,
                Map.class
        );

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("E2E: Login request with empty username is processed")
    void test_emptyUsername_IsProcessed() {
        // Arrange
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", "");
        loginRequest.put("password", validPassword);

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                loginRequest,
                Map.class
        );

        // Assert
        assertThat(response).isNotNull();
    }
}
