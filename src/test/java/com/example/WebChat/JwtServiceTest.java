package com.example.WebChat;

import com.example.WebChat.Service.JwtService;
import com.example.WebChat.UtilsConfigs.AppProperties;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {

        // Δημιουργούμε fake AppProperties
        AppProperties appProperties = new AppProperties();

        // Βάζουμε VALID Base64 secret (512-bit recommended)
        String rawSecret = "my-super-secret-key-for-testing-which-is-long-enough-123456";
        String base64Secret = Base64.getEncoder().encodeToString(rawSecret.getBytes());

        appProperties.getSecurity().setJwtSecret(base64Secret);
        appProperties.getSecurity().setJwtExpirationMs(3600000L); // 1 hour

        jwtService = new JwtService(appProperties);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldGenerateValidToken() {
        String username = "Mitsos";

        UserDetails userDetails = User.withUsername(username)
                .password("121221")
                .authorities("USER")
                .build();

        String token = jwtService.generateToken(userDetails);

        assertNotNull(token);
        assertEquals(username, jwtService.extractUsername(token));
    }

    @Test
    void shouldThrowForExpiredToken() {
        String expiredToken = jwtService.generateExpiredToken("Mitsos");

        assertThrows(ExpiredJwtException.class,
                () -> jwtService.extractUsername(expiredToken));
    }

    @Test
    void isTokenValid_ShouldReturnTrueForCorrectUser() {
        String username = "Mitsos";

        UserDetails user = User.withUsername(username)
                .password("p")
                .authorities("USER")
                .build();

        String token = jwtService.generateToken(user);

        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_ShouldReturnFalseForTamperedToken() {
        String username = "Mitsos";

        UserDetails user = User.withUsername(username)
                .password("p")
                .authorities("USER")
                .build();

        String token = jwtService.generateToken(user);

        // Αλλοιώνουμε το token
        String tamperedToken = token.substring(0, token.length() - 2) + "aa";

        assertFalse(jwtService.isTokenValid(tamperedToken));
    }
}
