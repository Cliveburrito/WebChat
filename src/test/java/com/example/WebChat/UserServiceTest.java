package com.example.WebChat;

import com.example.WebChat.DTO.JwtAuthenticationResponse;
import com.example.WebChat.DTO.RegisterUserRequest;
import com.example.WebChat.DTO.UserResponse;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Repository.*;
import com.example.WebChat.Service.JwtService;
import com.example.WebChat.Service.RateLimiterService;
import com.example.WebChat.Service.UserService;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.WebChat.Repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private RateLimiterService rateLimiter;
    @Mock private Bucket bucket;

    @InjectMocks
    private UserService userService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_ShouldReturnToken_WhenSuccessful() {
        // Arrange
        String ip = "127.0.0.1";
        RegisterUserRequest request = new RegisterUserRequest("mitsos", "test@test.com", "pass12344");

        when(rateLimiter.resolveAuthBucket(ip)).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");

        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setUsername("mitsos");
        savedUser.setPasswordHash("hashed_pass");


        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(any())).thenReturn("mocked_jwt_token");

        // Act
        JwtAuthenticationResponse response = userService.register(request, ip);

        // Assert
        assertNotNull(response);
        assertEquals("mocked_jwt_token", response.token());
        verify(userRepository).save(any(User.class));
    }
}

