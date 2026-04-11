package com.example.WebChat.auth;

import com.example.WebChat.auth.dto.AuthTokens;
import com.example.WebChat.auth.dto.LoginUserRequest;
import com.example.WebChat.auth.dto.RegisterUserRequest;
import com.example.WebChat.config.AppProperties;
import com.example.WebChat.observability.TrackingLog;
import com.example.WebChat.user.dto.UserResponse;
import com.example.WebChat.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private TrackingLog trackingLog;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private com.example.WebChat.Service.RateLimiterService rateLimiterService;

    @MockitoBean
    private AppProperties appProperties;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setUp() {
        AppProperties.Security security = new AppProperties.Security();
        security.setRefreshCookieSecure(false);
        security.setRefreshCookieSameSite("Strict");
        security.setRefreshExpirationMs(100000L);
        org.mockito.Mockito.lenient().when(appProperties.getSecurity()).thenReturn(security);
    }

    @Test
    @DisplayName("Should login successfully")
    void login_Success() throws Exception {
        LoginUserRequest request = new LoginUserRequest("testuser", "password123");
        UserResponse userResponse = new UserResponse(1L, "testuser", "test@test.com", null);
        AuthTokens tokens = new AuthTokens("access-token", "refresh-token", userResponse);

        when(userService.login(any(LoginUserRequest.class))).thenReturn(tokens);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access-token"))
                .andExpect(cookie().exists("webchat_refresh"))
                .andExpect(cookie().value("webchat_refresh", "refresh-token"));
    }

    @Test
    @DisplayName("Should register successfully")
    void register_Success() throws Exception {
        RegisterUserRequest request = new RegisterUserRequest("testuser", "test@test.com", "password123");
        UserResponse userResponse = new UserResponse(1L, "testuser", "test@test.com", null);
        AuthTokens tokens = new AuthTokens("access-token", "refresh-token", userResponse);

        when(userService.register(any(RegisterUserRequest.class))).thenReturn(tokens);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access-token"))
                .andExpect(cookie().exists("webchat_refresh"))
                .andExpect(cookie().value("webchat_refresh", "refresh-token"));
    }
}
