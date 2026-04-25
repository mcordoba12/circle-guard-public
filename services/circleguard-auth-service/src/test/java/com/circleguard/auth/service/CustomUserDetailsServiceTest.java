package com.circleguard.auth.service;

import com.circleguard.auth.model.*;
import com.circleguard.auth.repository.LocalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CustomUserDetailsService - User authentication and authority mapping.
 * Tests user loading from repository with proper role/permission authority conversion.
 *
 * Uses Mockito to mock LocalUserRepository and validates Spring Security authorities.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Custom User Details Service Tests")
class CustomUserDetailsServiceTest {

    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private LocalUserRepository localUserRepository;

    @BeforeEach
    void setUp() {
        customUserDetailsService = new CustomUserDetailsService(localUserRepository);
    }

    @Test
    @DisplayName("loadUserByUsername with existing active user returns UserDetails with proper authorities")
    void test_loadUserByUsername_WithActiveUser_ReturnsUserDetails() {
        // Arrange
        String username = "testuser";
        Permission readPerm = Permission.builder().name("READ_HEALTH").build();
        Permission writePerm = Permission.builder().name("WRITE_SURVEY").build();
        Role role = Role.builder()
                .name("USER")
                .permissions(Set.of(readPerm, writePerm))
                .build();
        LocalUser user = LocalUser.builder()
                .username(username)
                .password("encrypted_password")
                .isActive(true)
                .roles(Set.of(role))
                .build();

        when(localUserRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // Act
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

        // Assert
        assertNotNull(userDetails, "UserDetails should not be null");
        assertEquals(username, userDetails.getUsername(), "Username should match");
        assertEquals("encrypted_password", userDetails.getPassword(), "Password should be returned from user object");
        assertTrue(userDetails.isEnabled(), "User should be enabled when isActive is true");
    }

    @Test
    @DisplayName("loadUserByUsername includes ROLE_ prefixed role authority from user roles")
    void test_loadUserByUsername_IncludesRoleAuthority() {
        // Arrange
        String username = "testuser";
        Role adminRole = Role.builder()
                .name("ADMIN")
                .permissions(new HashSet<>())
                .build();
        LocalUser user = LocalUser.builder()
                .username(username)
                .password("pass")
                .isActive(true)
                .roles(Set.of(adminRole))
                .build();

        when(localUserRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // Act
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

        // Assert
        assertNotNull(userDetails.getAuthorities(), "Authorities should not be null");
        assertTrue(userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(auth -> auth.equals("ROLE_ADMIN")),
                "Authorities should include ROLE_ADMIN with proper ROLE_ prefix");
    }

    @Test
    @DisplayName("loadUserByUsername includes granular permission authorities from role permissions")
    void test_loadUserByUsername_IncludesPermissionAuthorities() {
        // Arrange
        String username = "testuser";
        Permission perm1 = Permission.builder().name("READ_HEALTH").build();
        Permission perm2 = Permission.builder().name("APPROVE_CERTIFICATE").build();
        Role role = Role.builder()
                .name("VALIDATOR")
                .permissions(Set.of(perm1, perm2))
                .build();
        LocalUser user = LocalUser.builder()
                .username(username)
                .password("pass")
                .isActive(true)
                .roles(Set.of(role))
                .build();

        when(localUserRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // Act
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
        Set<String> authorities = new HashSet<>();
        userDetails.getAuthorities().forEach(auth -> authorities.add(auth.getAuthority()));

        // Assert
        assertNotNull(userDetails.getAuthorities(), "Authorities should not be null");
        assertTrue(authorities.contains("READ_HEALTH"), "Should include READ_HEALTH permission");
        assertTrue(authorities.contains("APPROVE_CERTIFICATE"), "Should include APPROVE_CERTIFICATE permission");
        assertEquals(3, userDetails.getAuthorities().size(), "Should have 1 role + 2 permissions = 3 authorities");
    }

    @Test
    @DisplayName("loadUserByUsername with nonexistent user throws UsernameNotFoundException")
    void test_loadUserByUsername_WithNonexistentUser_ThrowsException() {
        // Arrange
        String nonexistentUsername = "nonexistent";
        when(localUserRepository.findByUsername(nonexistentUsername)).thenReturn(Optional.empty());

        // Act & Assert
        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername(nonexistentUsername),
                "Should throw UsernameNotFoundException for nonexistent user"
        );
        assertTrue(exception.getMessage().contains("nonexistent"), "Exception message should contain username");
    }

    @Test
    @DisplayName("loadUserByUsername with disabled user throws DisabledException")
    void test_loadUserByUsername_WithDisabledUser_ThrowsDisabledException() {
        // Arrange
        String username = "disableduser";
        LocalUser user = LocalUser.builder()
                .username(username)
                .password("pass")
                .isActive(false)
                .roles(new HashSet<>())
                .build();

        when(localUserRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // Act & Assert
        DisabledException exception = assertThrows(
                DisabledException.class,
                () -> customUserDetailsService.loadUserByUsername(username),
                "Should throw DisabledException when user.isActive is false"
        );
        assertTrue(exception.getMessage().contains("disabled"), "Exception message should mention disabled status");
    }

    @Test
    @DisplayName("loadUserByUsername invokes repository.findByUsername with correct username")
    void test_loadUserByUsername_InvokesRepository() {
        // Arrange
        String username = "testuser";
        LocalUser user = LocalUser.builder()
                .username(username)
                .password("pass")
                .isActive(true)
                .roles(new HashSet<>())
                .build();

        when(localUserRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // Act
        customUserDetailsService.loadUserByUsername(username);

        // Assert
        verify(localUserRepository, times(1)).findByUsername(username);
    }
}
