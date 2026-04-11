package com.example.WebChat.user;

import com.example.WebChat.auth.dto.CustomPrincipal;
import com.example.WebChat.auth.RefreshTokenService;
import com.example.WebChat.auth.dto.AuthTokens;
import com.example.WebChat.auth.dto.LoginUserRequest;
import com.example.WebChat.auth.dto.RegisterUserRequest;
import com.example.WebChat.user.User;
import com.example.WebChat.shared.EmailAlreadyExistsException;
import com.example.WebChat.shared.UserAlreadyExistsException;
import com.example.WebChat.user.UserRepository;
import com.example.WebChat.auth.JwtService;
import com.example.WebChat.presence.PresenceService;
import com.example.WebChat.user.dto.UserResponse;
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
    private PresenceService presenceService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private com.example.WebChat.observability.TrackingLog trackingLog;

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
    private final String REFRESH_TOKEN = "mocked.refresh.token";

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
            RegisterUserRequest request = new RegisterUserRequest(TEST_USERNAME, TEST_EMAIL, TEST_PASSWORD);

            when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(false);
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(HASHED_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(jwtService.generateToken(any(CustomPrincipal.class))).thenReturn(JWT_TOKEN);
            when(refreshTokenService.issue(testUser)).thenReturn(REFRESH_TOKEN);

            AuthTokens response = userService.register(request);

            assertThat(response.accessToken()).isEqualTo(JWT_TOKEN);
            assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(response.user()).isEqualTo(expectedUserResponse);

            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getUsername()).isEqualTo(TEST_USERNAME);
            assertThat(savedUser.getEmail()).isEqualTo(TEST_EMAIL);
            assertThat(savedUser.getPasswordHash()).isEqualTo(HASHED_PASSWORD);
            assertThat(savedUser.isEnabled()).isTrue();
            assertThat(savedUser.isStealthMode()).isFalse();
            assertThat(savedUser.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should throw UserAlreadyExists when username taken")
        void shouldThrowUserAlreadyExists() {
            // Arrange
            RegisterUserRequest request = new RegisterUserRequest(TEST_USERNAME, TEST_EMAIL, TEST_PASSWORD);

            when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> userService.register(request))
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
            assertThatThrownBy(() -> userService.register(request))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("Email already in use");

            verify(userRepository, never()).save(any(User.class));
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
            when(refreshTokenService.issue(testUser)).thenReturn(REFRESH_TOKEN);

            // Act
            AuthTokens response = userService.login(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.accessToken()).isEqualTo(JWT_TOKEN);
            assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
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
            assertThatThrownBy(() -> userService.login(request))
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
            assertThatThrownBy(() -> userService.login(request))
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

}
