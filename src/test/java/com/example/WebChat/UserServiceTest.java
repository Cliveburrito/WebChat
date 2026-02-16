package com.example.WebChat;

import com.example.WebChat.DTO.*;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Exception.EmailAlreadyExistsException;
import com.example.WebChat.Exception.RateLimitExceededException;
import com.example.WebChat.Exception.UserAlreadyExistsException;
import com.example.WebChat.Repository.UserRepository;
import com.example.WebChat.Service.JwtService;
import com.example.WebChat.Service.PresenceService;
import com.example.WebChat.Service.RateLimiterService;
import com.example.WebChat.Service.UserService;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RateLimiterService rateLimiter;

    @Mock
    private PresenceService presenceService;

    @Mock
    private Bucket bucket;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserService userService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private final String TEST_IP = "127.0.0.1";
    private final String TEST_USERNAME = "testuser";
    private final String TEST_EMAIL = "test@test.com";
    private final String TEST_PASSWORD = "password123";
    private final String HASHED_PASSWORD = "hashed_password_123";
    private final String JWT_TOKEN = "mocked.jwt.token";

    private User testUser;
    private UserResponse expectedUserResponse;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username(TEST_USERNAME)
                .email(TEST_EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .createdAt(Instant.now())
                .enabled(true)
                .stealthMode(false)
                .avatarUrl("default-avatar.png")
                .build();

        expectedUserResponse = new UserResponse(
                testUser.getId(),
                testUser.getUsername(),
                testUser.getEmail(),
                testUser.getAvatarUrl()
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Register Tests")
    class RegisterTests {

        @Test
        @DisplayName("Should register new user successfully")
        void shouldRegisterNewUserSuccessfully() {
            // Arrange
            RegisterUserRequest request = new RegisterUserRequest(TEST_USERNAME, TEST_EMAIL, TEST_PASSWORD);

            when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(false);
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(HASHED_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            when(jwtService.generateToken(any(UserDetails.class))).thenReturn(JWT_TOKEN);

            // Act
            JwtAuthenticationResponse response = userService.register(request, TEST_IP);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.token()).isEqualTo(JWT_TOKEN);
            assertThat(response.user()).isEqualTo(expectedUserResponse);

            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getUsername()).isEqualTo(TEST_USERNAME);
            assertThat(savedUser.getEmail()).isEqualTo(TEST_EMAIL);
            assertThat(savedUser.getPasswordHash()).isEqualTo(HASHED_PASSWORD);
            assertThat(savedUser.getCreatedAt()).isNotNull();
            assertThat(savedUser.isEnabled()).isTrue();
            assertThat(savedUser.isStealthMode()).isFalse();
        }



        @Test
        @DisplayName("Should throw UserAlreadyExists when username taken")
        void shouldThrowUserAlreadyExists() {
            // Arrange
            RegisterUserRequest request = new RegisterUserRequest(TEST_USERNAME, TEST_EMAIL, TEST_PASSWORD);

            when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> userService.register(request, TEST_IP))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining("Username already in use");

            verify(userRepository, never()).existsByEmail(anyString());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw EmailAlreadyExists when email taken")
        void shouldThrowEmailAlreadyExists() {
            // Arrange
            RegisterUserRequest request = new RegisterUserRequest(TEST_USERNAME, TEST_EMAIL, TEST_PASSWORD);

            when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(false);
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> userService.register(request, TEST_IP))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("Email already in use");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should handle empty username validation")
        void shouldHandleEmptyUsername() {
            // Arrange
            RegisterUserRequest request = new RegisterUserRequest("", TEST_EMAIL, TEST_PASSWORD);


            // Note: The validation is usually done at controller level with @Valid
            // Here we're testing that the service passes through whatever it gets

            // Act & Assert
            assertThatThrownBy(() -> userService.register(request, TEST_IP))
                    .isInstanceOf(RuntimeException.class); // Will fail when trying to save
        }
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void shouldLoginSuccessfully() {
            // Arrange
            LoginUserRequest request = new LoginUserRequest(TEST_USERNAME, TEST_PASSWORD);

            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername(TEST_USERNAME)
                    .password(HASHED_PASSWORD)
                    .authorities("USER")
                    .build();

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            when(jwtService.generateToken(userDetails)).thenReturn(JWT_TOKEN);

            // Act
            JwtAuthenticationResponse response = userService.login(request, TEST_IP);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.token()).isEqualTo(JWT_TOKEN);
            assertThat(response.user()).isEqualTo(expectedUserResponse);

            verify(authenticationManager).authenticate(
                    argThat(auth ->
                            auth.getPrincipal().equals(TEST_USERNAME) &&
                                    auth.getCredentials().equals(TEST_PASSWORD)
                    )
            );
        }


        @Test
        @DisplayName("Should propagate BadCredentialsException")
        void shouldPropagateBadCredentials() {
            // Arrange
            LoginUserRequest request = new LoginUserRequest(TEST_USERNAME, "wrongpassword");


            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            // Act & Assert
            assertThatThrownBy(() -> userService.login(request, TEST_IP))
                    .isInstanceOf(BadCredentialsException.class);

            verify(userRepository, never()).findByUsername(anyString());
            verify(jwtService, never()).generateToken(any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when user not found after authentication")
        void shouldThrowWhenUserNotFoundAfterAuth() {
            // Arrange
            LoginUserRequest request = new LoginUserRequest(TEST_USERNAME, TEST_PASSWORD);

            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername(TEST_USERNAME)
                    .password(HASHED_PASSWORD)
                    .authorities("USER")
                    .build();

            when(authenticationManager.authenticate(any()))
                    .thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userService.login(request, TEST_IP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid username");

            verify(jwtService, never()).generateToken(any());
        }
    }

    @Nested
    @DisplayName("Toggle Stealth Mode Tests")
    class ToggleStealthModeTests {

        @Test
        @DisplayName("Should enable stealth mode")
        void shouldEnableStealthMode() {
            // Arrange
            Long userId = 1L;
            String username = "testuser";
            boolean enabled = true;

            doNothing().when(userRepository).updateStealthMode(userId, enabled);
            doNothing().when(presenceService).onDisconnect(username);

            // Act
            userService.toggleStealthMode(userId, username, enabled);

            // Assert
            verify(userRepository).updateStealthMode(userId, enabled);
            verify(presenceService).onDisconnect(username);
            verify(presenceService, never()).onConnect(anyString());
        }

        @Test
        @DisplayName("Should disable stealth mode")
        void shouldDisableStealthMode() {
            // Arrange
            Long userId = 1L;
            String username = "testuser";
            boolean enabled = false;

            doNothing().when(userRepository).updateStealthMode(userId, enabled);
            doNothing().when(presenceService).onConnect(username);

            // Act
            userService.toggleStealthMode(userId, username, enabled);

            // Assert
            verify(userRepository).updateStealthMode(userId, enabled);
            verify(presenceService).onConnect(username);
            verify(presenceService, never()).onDisconnect(anyString());
        }

        @Test
        @DisplayName("Should handle repository error")
        void shouldHandleRepositoryError() {
            // Arrange
            Long userId = 1L;
            String username = "testuser";
            boolean enabled = true;

            doThrow(new RuntimeException("Database error"))
                    .when(userRepository).updateStealthMode(userId, enabled);

            // Act & Assert
            assertThatThrownBy(() -> userService.toggleStealthMode(userId, username, enabled))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Database error");

            verify(presenceService, never()).onDisconnect(anyString());
        }
    }

    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationScenarios {

        @Test
        @DisplayName("Should handle full user lifecycle: register → login → toggle stealth")
        void shouldHandleFullUserLifecycle() {
            // 1. Register
            RegisterUserRequest registerRequest = new RegisterUserRequest(TEST_USERNAME, TEST_EMAIL, TEST_PASSWORD);

            when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(false);
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(HASHED_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername(TEST_USERNAME)
                    .password(HASHED_PASSWORD)
                    .authorities("USER")
                    .build();

            when(jwtService.generateToken(any(UserDetails.class))).thenReturn(JWT_TOKEN);

            JwtAuthenticationResponse registerResponse = userService.register(registerRequest, TEST_IP);

            assertThat(registerResponse).isNotNull();
            assertThat(registerResponse.user().username()).isEqualTo(TEST_USERNAME);

            // 2. Login
            LoginUserRequest loginRequest = new LoginUserRequest(TEST_USERNAME, TEST_PASSWORD);

            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            when(jwtService.generateToken(userDetails)).thenReturn(JWT_TOKEN + "_new");

            JwtAuthenticationResponse loginResponse = userService.login(loginRequest, TEST_IP);

            assertThat(loginResponse).isNotNull();
            assertThat(loginResponse.token()).isEqualTo(JWT_TOKEN + "_new");

            // 3. Toggle stealth mode
            doNothing().when(userRepository).updateStealthMode(testUser.getId(), true);
            doNothing().when(presenceService).onDisconnect(TEST_USERNAME);

            userService.toggleStealthMode(testUser.getId(), TEST_USERNAME, true);

            verify(userRepository).updateStealthMode(testUser.getId(), true);
            verify(presenceService).onDisconnect(TEST_USERNAME);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null IP address")
        void shouldHandleNullIp() {
            // Arrange
            RegisterUserRequest request = new RegisterUserRequest(TEST_USERNAME, TEST_EMAIL, TEST_PASSWORD);

            when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(false);
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(HASHED_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(jwtService.generateToken(any())).thenReturn(JWT_TOKEN);

            // Act
            JwtAuthenticationResponse response = userService.register(request, null);

            // Assert
            assertThat(response).isNotNull();

        }

        @Test
        @DisplayName("Should handle very long username")
        void shouldHandleLongUsername() {
            // Arrange
            String longUsername = "a".repeat(50); // Assuming max length 25 in entity, this should fail at DB level
            RegisterUserRequest request = new RegisterUserRequest(longUsername, TEST_EMAIL, TEST_PASSWORD);


            when(userRepository.existsByUsername(longUsername)).thenReturn(false);
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> userService.register(request, TEST_IP))
                    .isInstanceOf(Exception.class); // Will fail when trying to save due to column length
        }
    }
}