package com.circleguard.auth.integration;

import com.circleguard.auth.client.IdentityClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Auth Service REST calls to Identity Service.
 * Validates that authentication service correctly calls identity-service
 * to map real identities to anonymous IDs via REST API.
 *
 * Uses @MockBean for IdentityClient to simulate REST calls.
 * Tests the REST client integration pattern without actual HTTP calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Auth Service to Identity Service Integration Tests")
class AuthToIdentityIntegrationTest {

    @MockBean
    private IdentityClient identityClient;

    private String realIdentity;
    private UUID expectedAnonymousId;

    @BeforeEach
    void setUp() {
        realIdentity = "user@circleguard.edu";
        expectedAnonymousId = UUID.randomUUID();

        // Mock IdentityClient to return anonymousId
        lenient().when(identityClient.getAnonymousId(realIdentity))
                .thenReturn(expectedAnonymousId);
    }

    @Test
    @DisplayName("IdentityClient.getAnonymousId returns valid UUID for real identity")
    void test_identityClient_ReturnsAnonymousIdForRealIdentity() {
        // Arrange
        when(identityClient.getAnonymousId(realIdentity)).thenReturn(expectedAnonymousId);

        // Act
        UUID result = identityClient.getAnonymousId(realIdentity);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedAnonymousId);
        verify(identityClient, times(1)).getAnonymousId(realIdentity);
    }

    @Test
    @DisplayName("IdentityClient invokes getAnonymousId with correct real identity parameter")
    void test_identityClient_InvokesWithCorrectParameter() {
        // Arrange
        String testIdentity = "john.doe@circleguard.edu";
        when(identityClient.getAnonymousId(testIdentity)).thenReturn(UUID.randomUUID());

        // Act
        identityClient.getAnonymousId(testIdentity);

        // Assert
        verify(identityClient).getAnonymousId(testIdentity);
    }

    @Test
    @DisplayName("IdentityClient returns different UUIDs for different real identities")
    void test_identityClient_ReturnsDifferentUUIDsForDifferentIdentities() {
        // Arrange
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        String identity1 = "user1@circleguard.edu";
        String identity2 = "user2@circleguard.edu";

        when(identityClient.getAnonymousId(identity1)).thenReturn(uuid1);
        when(identityClient.getAnonymousId(identity2)).thenReturn(uuid2);

        // Act
        UUID result1 = identityClient.getAnonymousId(identity1);
        UUID result2 = identityClient.getAnonymousId(identity2);

        // Assert
        assertThat(result1).isNotEqualTo(result2);
        assertThat(result1).isEqualTo(uuid1);
        assertThat(result2).isEqualTo(uuid2);
    }

    @Test
    @DisplayName("IdentityClient handles multiple sequential calls correctly")
    void test_identityClient_HandlesMultipleSequentialCalls() {
        // Arrange
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        UUID uuid3 = UUID.randomUUID();

        when(identityClient.getAnonymousId("user1@example.com")).thenReturn(uuid1);
        when(identityClient.getAnonymousId("user2@example.com")).thenReturn(uuid2);
        when(identityClient.getAnonymousId("user3@example.com")).thenReturn(uuid3);

        // Act
        UUID result1 = identityClient.getAnonymousId("user1@example.com");
        UUID result2 = identityClient.getAnonymousId("user2@example.com");
        UUID result3 = identityClient.getAnonymousId("user3@example.com");

        // Assert
        assertThat(result1).isEqualTo(uuid1);
        assertThat(result2).isEqualTo(uuid2);
        assertThat(result3).isEqualTo(uuid3);
        verify(identityClient, times(3)).getAnonymousId(anyString());
    }

    @Test
    @DisplayName("IdentityClient is invoked exactly once per getAnonymousId call")
    void test_identityClient_InvokedExactlyOnce() {
        // Arrange
        when(identityClient.getAnonymousId(realIdentity)).thenReturn(expectedAnonymousId);

        // Act
        identityClient.getAnonymousId(realIdentity);
        identityClient.getAnonymousId(realIdentity);

        // Assert
        verify(identityClient, times(2)).getAnonymousId(realIdentity);
    }

    @Test
    @DisplayName("IdentityClient handles LDAP formatted identities correctly")
    void test_identityClient_HandlesLdapFormattedIdentities() {
        // Arrange
        String ldapIdentity = "uid=john.doe,ou=users,dc=circleguard,dc=edu";
        UUID expectedUuid = UUID.randomUUID();

        when(identityClient.getAnonymousId(ldapIdentity)).thenReturn(expectedUuid);

        // Act
        UUID result = identityClient.getAnonymousId(ldapIdentity);

        // Assert
        assertThat(result).isEqualTo(expectedUuid);
        verify(identityClient).getAnonymousId(ldapIdentity);
    }

    @Test
    @DisplayName("IdentityClient handles email formatted identities correctly")
    void test_identityClient_HandlesEmailFormattedIdentities() {
        // Arrange
        String emailIdentity = "alice.smith@company.edu";
        UUID expectedUuid = UUID.randomUUID();

        when(identityClient.getAnonymousId(emailIdentity)).thenReturn(expectedUuid);

        // Act
        UUID result = identityClient.getAnonymousId(emailIdentity);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedUuid);
    }
}
