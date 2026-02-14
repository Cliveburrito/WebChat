package com.example.WebChat;

import com.example.WebChat.UtilsConfigs.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Setter
    @Getter
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter; // Mock the filter to avoid actual JWT processing

    @BeforeEach
    void setUp() throws Exception {
        // 🛡️ Λέμε στο mock φίλτρο να συνεχίζει την αλυσίδα αντί να σταματάει το request
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("Public endpoints should be accessible without authentication")
    void publicEndpointsShouldBeAccessible() throws Exception {
        // Auth endpoints
        mockMvc.perform(post("/api/auth/register"))
                .andExpect(status().isBadRequest()); // Bad request is fine - means it reached the controller

        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isBadRequest());

        // WebSocket endpoint
        mockMvc.perform(get("/ws/info"))
                .andExpect(status().isOk());

        // File download
        mockMvc.perform(get("/api/files/download/test.txt"))
                .andExpect(status().isNotFound()); // Not found is fine - means it reached the controller

        // Actuator endpoints
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Protected endpoints should return 401/403 without authentication")
    void protectedEndpointsShouldRequireAuth() throws Exception {
        // Chat endpoints
        mockMvc.perform(get("/api/chats/my"))
                .andExpect(status().isUnauthorized()); // or isForbidden() depending on config

        mockMvc.perform(post("/api/chats/direct"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/chats/group"))
                .andExpect(status().isUnauthorized());

        // User endpoints
        mockMvc.perform(get("/api/users/getall"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/getuser/test"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/users/me/stealth?enabled=true"))
                .andExpect(status().isUnauthorized());

        // Conversation endpoints
        mockMvc.perform(post("/api/conversations/direct"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockCustomUser()
    @DisplayName("Protected endpoints should be accessible with authentication")
    void protectedEndpointsShouldBeAccessibleWithAuth() throws Exception {
        // Using @WithMockUser to simulate authentication
        // These should now pass authentication and hit the controllers

        mockMvc.perform(get("/api/chats/my"))
                .andExpect(status().isOk()); // Should return empty list or actual data
    }

//    @Test
//    @DisplayName("Should handle CORS correctly")
//    void corsShouldBeConfigured() throws Exception {
//        mockMvc.perform(get("/api/chats/my")
//                        .header("Origin", "http://localhost:3000")
//                        .header("Access-Control-Request-Method", "GET"))
//                .andExpect(status().isUnauthorized()) // Still unauthorized but CORS headers should be present
//                .andExpect(header().exists("Access-Control-Allow-Origin"));
//    }

}