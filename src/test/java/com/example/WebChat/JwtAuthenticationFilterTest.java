package com.example.WebChat;

import com.example.WebChat.DTO.CustomPrincipal;
import com.example.WebChat.Service.JwtService;
import com.example.WebChat.UtilsConfigs.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should authenticate user with valid JWT")
    void shouldAuthenticateWithValidToken() throws Exception {
        // Given
        String token = "valid.jwt.token";
        String username = "testuser";
        Long userId = 123L;

        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUsername(token)).thenReturn(username);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractAuthorities(token)).thenReturn(Collections.emptyList());

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(CustomPrincipal.class);

        CustomPrincipal principal = (CustomPrincipal) authentication.getPrincipal();
        assertThat(principal.id()).isEqualTo(userId);
        assertThat(principal.username()).isEqualTo(username);
        assertThat(principal.getPassword()).isNull(); // Password should be null in token auth
    }

    @Test
    @DisplayName("Should skip authentication when no Authorization header")
    void shouldSkipWhenNoAuthHeader() throws Exception {
        // Given - no header added

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).isTokenValid(anyString());
    }

    @Test
    @DisplayName("Should skip authentication when header doesn't start with Bearer")
    void shouldSkipWhenNotBearerToken() throws Exception {
        // Given
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).isTokenValid(anyString());
    }

    @Test
    @DisplayName("Should skip authentication for actuator prometheus endpoint")
    void shouldSkipForActuatorPrometheus() throws Exception {
        // Given
        request.setRequestURI("/actuator/prometheus");
        request.addHeader("Authorization", "Bearer some.token");

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).isTokenValid(anyString());
    }

    @Test
    @DisplayName("Should not authenticate when token is invalid")
    void shouldNotAuthenticateWithInvalidToken() throws Exception {
        // Given
        String token = "invalid.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.isTokenValid(token)).thenReturn(false);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        // Verify we didn't try to extract claims from invalid token
        verify(jwtService, never()).extractUsername(anyString());
        verify(jwtService, never()).extractUserId(anyString());
    }

    @Test
    @DisplayName("Should not override existing authentication")
    void shouldNotOverrideExistingAuth() throws Exception {
        // Given
        String token = "valid.token";
        request.addHeader("Authorization", "Bearer " + token);

        // Set existing authentication
        Authentication existingAuth = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUsername(token)).thenReturn("testuser");

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);

        // Existing authentication should remain
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isSameAs(existingAuth);
    }

    @Test
    @DisplayName("Should extract and set authorities from token")
    void shouldExtractAndSetAuthorities() throws Exception {
        // Given
        String token = "token.with.roles";
        var authorities = Collections.singletonList(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")
        );

        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUsername(token)).thenReturn("testuser");
        when(jwtService.extractUserId(token)).thenReturn(123L);
        doReturn(authorities).when(jwtService).extractAuthorities(token);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getAuthorities())
                .hasSize(1)
                .extracting("authority")
                .contains("ROLE_USER");
    }

    @Test
    @DisplayName("Should handle null username from token")
    void shouldHandleNullUsername() throws Exception {
        // Given
        String token = "token.with.null.username";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUsername(token)).thenReturn(null);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}