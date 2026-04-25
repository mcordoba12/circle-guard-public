package com.circleguard.auth.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.security.Key;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test suite para JwtAuthenticationFilter.
 * Valida que:
 * - Tokens válidos permiten continuar la cadena de filtros y establecen el contexto de seguridad
 * - Tokens expirados se rechazan sin establecer autenticación
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Tests")
class JwtAuthenticationFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private String jwtSecret = "my-super-secret-dev-key-32-chars-long-12345678";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtSecret);
    }

    /**
     * Test: validTokenAllowsAccess
     * Valida que un token JWT válido:
     * - Permite continuar la cadena de filtros
     * - Establece el contexto de seguridad con el usuario correcto
     * - Asigna los permisos correctamente
     */
    @Test
    @DisplayName("Token válido debe permitir acceso y establecer contexto de seguridad")
    void test_validTokenAllowsAccess() throws ServletException, IOException {
        // Arrange: crear un token JWT válido
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 3600000);

        String validToken = Jwts.builder()
                .setSubject("user123")
                .claim("permissions", List.of("READ", "WRITE"))
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(key)
                .compact();

        // Configure el mock para retornar el token en el header
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);

        // Act: ejecutar el filtro
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        // 1. Verifica que la cadena de filtros continúe
        verify(filterChain, times(1)).doFilter(request, response);

        // 2. Verifica que el contexto de seguridad se estableció
        assertNotNull(SecurityContextHolder.getContext().getAuthentication(),
                "El contexto de seguridad debe estar establecido");

        // 3. Verifica que el usuario es el correcto
        assertEquals("user123", SecurityContextHolder.getContext().getAuthentication().getPrincipal(),
                "El principal debe ser user123");
    }

    /**
     * Test: expiredTokenThrowsException
     * Valida que un token JWT expirado:
     * - No establece el contexto de seguridad
     * - Continúa la cadena de filtros (sin autenticación)
     * - Limpia el contexto de seguridad
     */
    @Test
    @DisplayName("Token expirado debe ser rechazado y no establecer contexto")
    void test_expiredTokenThrowsException() throws ServletException, IOException {
        // Arrange: crear un token JWT expirado
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        Date now = new Date();
        Date expiration = new Date(now.getTime() - 1000); // Expirado hace 1 segundo

        String expiredToken = Jwts.builder()
                .setSubject("user123")
                .claim("permissions", List.of("READ"))
                .setIssuedAt(new Date(now.getTime() - 7200000))
                .setExpiration(expiration)
                .signWith(key)
                .compact();

        // Configure el mock para retornar el token expirado
        when(request.getHeader("Authorization")).thenReturn("Bearer " + expiredToken);

        // Act: ejecutar el filtro
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        // 1. Verifica que la cadena de filtros continúe (incluso con token inválido)
        verify(filterChain, times(1)).doFilter(request, response);

        // 2. Verifica que NO se estableció autenticación
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "No debe establecerse autenticación para token expirado");

        // 3. Verifica que el contexto está limpio (sin credenciales)
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "El contexto debe estar limpio");
    }

    /**
     * Test: missingAuthorizationHeader
     * Valida que sin header Authorization:
     * - El filtro continúa sin establecer autenticación
     * - No lanza excepciones
     */
    @Test
    @DisplayName("Solicitud sin Authorization header debe continuar sin autenticación")
    void test_missingAuthorizationHeader() throws ServletException, IOException {
        // Arrange: sin header Authorization
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "No debe haber autenticación sin header");
    }

    /**
     * Test: malformedToken
     * Valida que un token malformado:
     * - No establece autenticación
     * - Continúa la cadena de filtros
     */
    @Test
    @DisplayName("Token malformado debe ser rechazado sin excepción")
    void test_malformedToken() throws ServletException, IOException {
        // Arrange: token malformado
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token.format");

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "No debe establecerse autenticación para token malformado");
    }
}
