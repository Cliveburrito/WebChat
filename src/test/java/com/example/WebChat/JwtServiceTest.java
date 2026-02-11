package com.example.WebChat;


import com.example.WebChat.Service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    UserDetails userDetails;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        // Manually initialize or use ReflectionTestUtils to set the secret key
        // if it's injected via @Value
        jwtService = new JwtService();
         // secret key toulaxiston 32 pshfia
        ReflectionTestUtils.setField(jwtService, "SECRET_KEY", "1234567891012344433453567891012345678910123");
       // ReflectionTestUtils.setField(jwtService, "expiration", 3600000L); // 1 hour
    }

    @Test
    void shouldGenerateValidToken() {
        String username = "Mitsos";
        userDetails = User.withUsername(username)
                .password("121221")
                .authorities("USER")
                .build();

        String token = jwtService.generateToken(userDetails);

        assertNotNull(token);
        assertEquals(username, jwtService.extractUsername(token));
    }

    @Test
    void shouldReturnFalseForExpiredToken() {
        String expiredToken = jwtService.generateExpiredToken("Mitsos");

        assertThrows(ExpiredJwtException.class, () -> jwtService.extractUsername(expiredToken));
    }


    @Test
    void isTokenValid_ShouldReturnTrueForCorrectUser() {
        String username = "Mitsos";
        UserDetails user = User.withUsername(username).password("p").authorities("U").build();
        String token = jwtService.generateToken(user);

        assertTrue(jwtService.isTokenValid(token));
    }
}