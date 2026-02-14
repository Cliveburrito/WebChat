package com.example.WebChat;

import com.example.WebChat.DTO.CustomPrincipal;
import com.example.WebChat.Service.JwtService;
import com.example.WebChat.UtilsConfigs.AppProperties;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private JwtService jwtService;
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();

        // Create a valid Base64 secret (512-bit recommended for HS256)
        String rawSecret = "my-super-secret-key-for-testing-which-is-long-enough-12345678901234567890";
        String base64Secret = Base64.getEncoder().encodeToString(rawSecret.getBytes());

        appProperties.getSecurity().setJwtSecret(base64Secret);
        appProperties.getSecurity().setJwtExpirationMs(3600000L); // 1 hour

        jwtService = new JwtService(appProperties);
    }

    @Nested
    @DisplayName("Token Generation Tests")
    class TokenGenerationTests {

        @Test
        @DisplayName("Should generate valid token for CustomPrincipal")
        void shouldGenerateTokenForCustomPrincipal() {
            // Given
            Long userId = 123L;
            String username = "testuser";
            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_USER")
            );

            CustomPrincipal principal = new CustomPrincipal(
                    userId,
                    username,
                    "password",
                    false,
                    true,
                    authorities
            );

            // When
            String token = jwtService.generateToken(principal);

            // Then
            assertThat(token).isNotNull();
            assertThat(jwtService.extractUsername(token)).isEqualTo(username);
            assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
            assertThat(jwtService.extractAuthorities(token))
                    .hasSize(1)
                    .extracting("authority")
                    .contains("ROLE_USER");
        }

        @Test
        @DisplayName("Should generate token with custom claims")
        void shouldGenerateTokenWithCustomClaims() {
            // Given
            String username = "testuser";
            CustomPrincipal principal = createPrincipal(username);

            // When
            String token = jwtService.generateToken(principal);

            // Then
            assertThat(token).isNotNull();
            assertThat(jwtService.extractUsername(token)).isEqualTo(username);
        }
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        @Test
        @DisplayName("Should validate valid token")
        void shouldValidateValidToken() {
            // Given
            CustomPrincipal principal = createPrincipal("validuser");
            String token = jwtService.generateToken(principal);

            // When
            boolean isValid = jwtService.isTokenValid(token);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("Should reject tampered token")
        void shouldRejectTamperedToken() {
            // Given
            CustomPrincipal principal = createPrincipal("testuser");
            String token = jwtService.generateToken(principal);

            // Tamper with the token (modify last character)
            String tamperedToken = token.substring(0, token.length() - 2) + "xx";

            // When
            boolean isValid = jwtService.isTokenValid(tamperedToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("Should reject expired token")
        void shouldRejectExpiredToken() {
            // Given
            String username = "expireduser";
            String expiredToken = jwtService.generateExpiredToken(username);

            // When/Then
            assertThrows(ExpiredJwtException.class, () -> jwtService.extractUsername(expiredToken));

            // isTokenValid should return false (not throw)
            assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
        }

        @Test
        @DisplayName("Should reject malformed token")
        void shouldRejectMalformedToken() {
            // Given
            String malformedToken = "not.a.valid.token";

            // When
            boolean isValid = jwtService.isTokenValid(malformedToken);

            // Then
            assertThat(isValid).isFalse();
        }
    }

    @Nested
    @DisplayName("Claim Extraction Tests")
    class ClaimExtractionTests {

        @Test
        @DisplayName("Should extract username from token")
        void shouldExtractUsername() {
            // Given
            String username = "extractuser";
            CustomPrincipal principal = createPrincipal(username);
            String token = jwtService.generateToken(principal);

            // When
            String extractedUsername = jwtService.extractUsername(token);

            // Then
            assertThat(extractedUsername).isEqualTo(username);
        }

        @Test
        @DisplayName("Should extract user ID from token")
        void shouldExtractUserId() {
            // Given
            Long userId = 456L;
            CustomPrincipal principal = new CustomPrincipal(
                    userId,
                    "testuser",
                    "pass",
                    false,
                    true,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            String token = jwtService.generateToken(principal);

            // When
            Long extractedUserId = jwtService.extractUserId(token);

            // Then
            assertThat(extractedUserId).isEqualTo(userId);
        }

        @Test
        @DisplayName("Should extract authorities from token")
        void shouldExtractAuthorities() {
            // Given
            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_ADMIN")
            );

            CustomPrincipal principal = new CustomPrincipal(
                    1L,
                    "adminuser",
                    "pass",
                    false,
                    true,
                    authorities
            );

            String token = jwtService.generateToken(principal);

            // When
            var extractedAuthorities = jwtService.extractAuthorities(token);

            // Then
            assertThat(extractedAuthorities)
                    .hasSize(2)
                    .extracting("authority")
                    .containsExactly("ROLE_USER", "ROLE_ADMIN");
        }
    }

    private CustomPrincipal createPrincipal(String username) {
        return new CustomPrincipal(
                1L,
                username,
                "password",
                false,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}