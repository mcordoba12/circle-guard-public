package com.circleguard.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test suite para DualChainAuthenticationProvider.
 * Valida que:
 * - Los usuarios LDAP se autentican correctamente cuando LDAP está disponible
 * - Los usuarios locales se autentican como fallback cuando LDAP falla
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DualChainAuthenticationProvider Tests")
class DualChainAuthenticationProviderTest {

    @Mock
    private LdapAuthenticationProvider ldapProvider;

    @Mock
    private DaoAuthenticationProvider localProvider;

    @Mock
    private Authentication ldapAuthResult;

    @Mock
    private Authentication localAuthResult;

    private DualChainAuthenticationProvider dualChainProvider;

    @BeforeEach
    void setUp() {
        dualChainProvider = new DualChainAuthenticationProvider(ldapProvider, localProvider);
    }

    /**
     * Test: ldapUserAuthenticatesSuccessfully
     * Valida que cuando LDAP está disponible y válido:
     * - Se retorna el resultado de autenticación LDAP
     * - No se intenta autenticar contra la BD local
     * - La autenticación es exitosa
     */
    @Test
    @DisplayName("Usuario LDAP debe autenticarse exitosamente sin intentar fallback local")
    void test_ldapUserAuthenticatesSuccessfully() {
        // Arrange
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                "ldapuser", "password123"
        );

        // Mock: LDAP provider retorna autenticación exitosa
        when(ldapProvider.authenticate(any(Authentication.class)))
                .thenReturn(ldapAuthResult);

        // Act
        Authentication result = dualChainProvider.authenticate(authToken);

        // Assert
        // 1. Verifica que la autenticación fue exitosa (retorna el resultado de LDAP)
        assertNotNull(result, "La autenticación debe retornar un resultado");
        assertEquals(ldapAuthResult, result, "Debe retornar el resultado de LDAP");

        // 2. Verifica que LDAP fue invocado
        verify(ldapProvider, times(1)).authenticate(any(Authentication.class));

        // 3. Verifica que LOCAL NO fue invocado (no hay fallback)
        verify(localProvider, never()).authenticate(any(Authentication.class));
    }

    /**
     * Test: localUserAuthenticatesAsFallback
     * Valida que cuando LDAP falla:
     * - Se intenta autenticar contra la BD local como fallback
     * - Se retorna el resultado de autenticación local
     * - La autenticación es exitosa
     */
    @Test
    @DisplayName("Usuario local debe autenticarse cuando LDAP falla (fallback)")
    void test_localUserAuthenticatesAsFallback() {
        // Arrange
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                "localuser", "password456"
        );

        // Mock: LDAP falla, local tiene éxito
        when(ldapProvider.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("LDAP failed"));
        when(localProvider.authenticate(any(Authentication.class)))
                .thenReturn(localAuthResult);

        // Act
        Authentication result = dualChainProvider.authenticate(authToken);

        // Assert
        // 1. Verifica que la autenticación fue exitosa (retorna el resultado de local)
        assertNotNull(result, "La autenticación debe retornar un resultado desde local");
        assertEquals(localAuthResult, result, "Debe retornar el resultado del proveedor local");

        // 2. Verifica que LDAP fue invocado primero
        verify(ldapProvider, times(1)).authenticate(any(Authentication.class));

        // 3. Verifica que LOCAL fue invocado como fallback
        verify(localProvider, times(1)).authenticate(any(Authentication.class));
    }

    /**
     * Test: bothChainsFailThrowsException
     * Valida que cuando ambos (LDAP y local) fallan:
     * - Se lanza una excepción de autenticación
     * - El usuario NO está autenticado
     */
    @Test
    @DisplayName("Debe lanzar excepción cuando ambas cadenas fallan")
    void test_bothChainsFailThrowsException() {
        // Arrange
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                "unknownuser", "wrongpassword"
        );

        // Mock: ambos fallan
        when(ldapProvider.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("LDAP failed"));
        when(localProvider.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Local DB failed"));

        // Act & Assert
        // 1. Verifica que se lanza excepción
        assertThrows(AuthenticationException.class, () -> {
            dualChainProvider.authenticate(authToken);
        }, "Debe lanzar AuthenticationException cuando ambas cadenas fallan");

        // 2. Verifica que ambos proveedores fueron invocados
        verify(ldapProvider, times(1)).authenticate(any(Authentication.class));
        verify(localProvider, times(1)).authenticate(any(Authentication.class));
    }

    /**
     * Test: supportsUsernamePasswordAuthentication
     * Valida que el proveedor soporta UsernamePasswordAuthenticationToken
     */
    @Test
    @DisplayName("Debe soportar UsernamePasswordAuthenticationToken")
    void test_supportsUsernamePasswordAuthentication() {
        // Act
        boolean supports = dualChainProvider.supports(UsernamePasswordAuthenticationToken.class);

        // Assert
        assertTrue(supports, "Debe soportar UsernamePasswordAuthenticationToken");
    }

    /**
     * Test: doesNotSupportOtherTokenTypes
     * Valida que el proveedor NO soporta otros tipos de tokens
     */
    @Test
    @DisplayName("No debe soportar otros tipos de tokens de autenticación")
    void test_doesNotSupportOtherTokenTypes() {
        // Act
        boolean supports = dualChainProvider.supports(String.class);

        // Assert
        assertFalse(supports, "No debe soportar otros tipos de autenticación");
    }
}
