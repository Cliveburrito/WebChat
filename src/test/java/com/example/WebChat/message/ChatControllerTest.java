package com.example.WebChat.message;

import com.example.WebChat.message.ChatController;
import com.example.WebChat.message.MessageQueryService;
import com.example.WebChat.message.dto.ChatMessageRequest;
import com.example.WebChat.auth.dto.CustomPrincipal;
import com.example.WebChat.auth.JwtService;
import com.example.WebChat.message.MessageService;
import com.example.WebChat.Service.RateLimiterService;
import com.example.WebChat.shared.security.WithMockCustomUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private RateLimiterService rateLimiterService;


    @MockitoBean private MessageService messageService;
    @MockitoBean private MessageQueryService messageQueryService;
    @MockitoBean private MessageReactionService messageReactionService;
    @MockitoBean private SimpMessagingTemplate messagingTemplate;
    @MockitoBean private com.example.WebChat.config.AppProperties appProperties;
    @MockitoBean private com.example.WebChat.observability.TrackingLog trackingLog;

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_USERNAME = "testuser";
    private static final Long CONVERSATION_ID = 100L;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        com.example.WebChat.config.AppProperties.Tracking tracking = new com.example.WebChat.config.AppProperties.Tracking();
        tracking.setDetailedEnabled(false);
        org.mockito.Mockito.lenient().when(appProperties.getTracking()).thenReturn(tracking);
    }

    private UsernamePasswordAuthenticationToken authWithCustomPrincipal() {
        CustomPrincipal principal = new CustomPrincipal(
                TEST_USER_ID, TEST_USERNAME, "123", false, false, null);

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    @DisplayName("POST /api/messages/chat/{id}/smsg - Should send message via REST (202)")
    @WithMockCustomUser()
    void sendMessage_ShouldAcceptMessage() throws Exception {
        ChatMessageRequest request = new ChatMessageRequest("Hello, world!", "temp-123-abc", null);

        doNothing().when(messageService).processAndSend(
                eq(TEST_USER_ID),
                eq(CONVERSATION_ID),
                eq("Hello, world!"),
                eq("temp-123-abc"),
                isNull()
        );

        mockMvc.perform(post("/api/messages/chat/{id}/smsg", CONVERSATION_ID)
                        .with(authentication(authWithCustomPrincipal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        verify(messageService).processAndSend(
                eq(TEST_USER_ID),
                eq(CONVERSATION_ID),
                eq("Hello, world!"),
                eq("temp-123-abc"),
                isNull()
        );
    }

    @Test
    @DisplayName("POST /api/messages/chat/{id}/smsg - Should return 401 without authentication")
    void sendMessage_WithoutAuth_ShouldReturnUnauthorized() throws Exception {
        ChatMessageRequest request = new ChatMessageRequest("Hello", "temp-123", null);

        mockMvc.perform(post("/api/messages/chat/{id}/smsg", CONVERSATION_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(messageService, never()).processAndSend(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("POST /api/messages/chat/{id}/smsg - Should handle empty content (still 202)")
    @WithMockCustomUser()
    void sendMessage_WithEmptyContent_ShouldStillProcess() throws Exception {
        ChatMessageRequest request = new ChatMessageRequest("", "temp-123", null);

        doNothing().when(messageService).processAndSend(
                anyLong(), anyLong(), anyString(), anyString(), any()
        );

        mockMvc.perform(post("/api/messages/chat/{id}/smsg", CONVERSATION_ID)
                        .with(authentication(authWithCustomPrincipal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        verify(messageService).processAndSend(
                eq(TEST_USER_ID),
                eq(CONVERSATION_ID),
                eq(""),
                eq("temp-123"),
                isNull()
        );
    }
}
