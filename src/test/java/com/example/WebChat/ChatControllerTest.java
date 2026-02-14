package com.example.WebChat;

import com.example.WebChat.Controller.ChatController;
import com.example.WebChat.DTO.ChatMessageRequest;
import com.example.WebChat.DTO.CustomPrincipal;
import com.example.WebChat.Service.JwtService;
import com.example.WebChat.Service.MessageService;
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


    @MockitoBean private MessageService messageService;
    @MockitoBean private SimpMessagingTemplate messagingTemplate;

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_USERNAME = "testuser";
    private static final Long CONVERSATION_ID = 100L;

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
    void sendMessage_ShouldAcceptMessage() throws Exception {
        ChatMessageRequest request = new ChatMessageRequest("Hello, world!", "temp-123-abc");

        doNothing().when(messageService).processAndSend(
                eq(TEST_USER_ID),
                eq(TEST_USERNAME),
                eq(CONVERSATION_ID),
                eq("Hello, world!"),
                eq("temp-123-abc")
        );

        mockMvc.perform(post("/api/messages/chat/{id}/smsg", CONVERSATION_ID)
                        .with(authentication(authWithCustomPrincipal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        verify(messageService).processAndSend(
                eq(TEST_USER_ID),
                eq(TEST_USERNAME),
                eq(CONVERSATION_ID),
                eq("Hello, world!"),
                eq("temp-123-abc")
        );
    }

    @Test
    @DisplayName("POST /api/messages/chat/{id}/smsg - Should return 401 without authentication")
    void sendMessage_WithoutAuth_ShouldReturnUnauthorized() throws Exception {
        ChatMessageRequest request = new ChatMessageRequest("Hello", "temp-123");

        mockMvc.perform(post("/api/messages/chat/{id}/smsg", CONVERSATION_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(messageService, never()).processAndSend(anyLong(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /api/messages/chat/{id}/smsg - Should handle empty content (still 202)")
    void sendMessage_WithEmptyContent_ShouldStillProcess() throws Exception {
        ChatMessageRequest request = new ChatMessageRequest("", "temp-123");

        doNothing().when(messageService).processAndSend(
                anyLong(), anyString(), anyLong(), anyString(), anyString()
        );

        mockMvc.perform(post("/api/messages/chat/{id}/smsg", CONVERSATION_ID)
                        .with(authentication(authWithCustomPrincipal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        verify(messageService).processAndSend(
                eq(TEST_USER_ID),
                eq(TEST_USERNAME),
                eq(CONVERSATION_ID),
                eq(""),
                eq("temp-123")
        );
    }
}
