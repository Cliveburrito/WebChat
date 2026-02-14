package com.example.WebChat;

import com.example.WebChat.Controller.ConversationController;
import com.example.WebChat.DTO.*;
import com.example.WebChat.Service.ConversationService;
import com.example.WebChat.Service.JwtService;
import com.example.WebChat.Service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConversationController.class)
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConversationService conversationService;

    @MockitoBean
    private MessageService messageService;

    private final Long TEST_USER_ID = 1L;
    private final String TEST_USERNAME = "testuser";

    @Test
    @DisplayName("POST /api/chats/direct - Should open direct chat")
    @WithMockCustomUser()
    void openDirectChat_ShouldReturnConversation() throws Exception {
        // Given
        OpenDirectChatRequest request = new OpenDirectChatRequest(1L, 2L);
        ConversationResponse expectedResponse = new ConversationResponse(
                100L, "otheruser", "avatar.png", "Hello", 0, Instant.now()
        );

        when(conversationService.openDirectChatPreview(1L, 2L))
                .thenReturn(expectedResponse);

        // When/Then
        mockMvc.perform(post("/api/chats/direct")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.name").value("otheruser"))
                .andExpect(jsonPath("$.lastMessage").value("Hello"));

        verify(conversationService).openDirectChatPreview(1L, 2L);
    }

    @Test
    @DisplayName("POST /api/chats/group - Should create group chat")
    @WithMockCustomUser()
    void createGroup_ShouldReturnGroupConversation() throws Exception {
        // Given
        OpenGroupChatRequest request = new OpenGroupChatRequest(
                "Test Group",
                List.of(2L, 3L, 4L)
        );

        ConversationResponse expectedResponse = new ConversationResponse(
                200L, "Test Group", "group-avatar.png", "Welcome!", 0, Instant.now()
        );

        when(conversationService.createGroupChatPreview(any(OpenGroupChatRequest.class), eq(TEST_USER_ID)))
                .thenReturn(expectedResponse);

        // When/Then
        mockMvc.perform(post("/api/chats/group")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(200L))
                .andExpect(jsonPath("$.name").value("Test Group"));

        verify(conversationService).createGroupChatPreview(any(OpenGroupChatRequest.class), eq(TEST_USER_ID));
    }

    @Test
    @DisplayName("POST /api/chats/group - Should return 400 for invalid group request")
    @WithMockCustomUser()
    void createGroup_WithInvalidRequest_ShouldReturnBadRequest() throws Exception {
        // Given - Empty group name and less than 2 members
        OpenGroupChatRequest invalidRequest = new OpenGroupChatRequest(
                "",  // Blank name
                List.of(2L) // Only one member (needs at least 2)
        );

        // When/Then
        mockMvc.perform(post("/api/chats/group")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(conversationService, never()).createGroupChatPreview(any(), any());
    }

    @Test
    @DisplayName("GET /api/chats/my - Should return user's chats")
    @WithMockCustomUser()
    void getMyChats_ShouldReturnListOfConversations() throws Exception {
        // Given
        List<ConversationResponse> expectedChats = List.of(
                new ConversationResponse(1L, "Chat1", "avatar1.png", "Hi", 2, Instant.now()),
                new ConversationResponse(2L, "Chat2", "avatar2.png", "Hello", 0, Instant.now())
        );

        when(conversationService.getUserChats(TEST_USER_ID)).thenReturn(expectedChats);

        // When/Then
        mockMvc.perform(get("/api/chats/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("Chat1"))
                .andExpect(jsonPath("$[0].lastMessage").value("Hi"))
                .andExpect(jsonPath("$[1].id").value(2L))
                .andExpect(jsonPath("$[1].name").value("Chat2"))
                .andExpect(jsonPath("$[1].lastMessage").value("Hello"))
                .andExpect(jsonPath("$.length()").value(2));

        verify(conversationService).getUserChats(TEST_USER_ID);
    }

    @Test
    @DisplayName("POST /api/chats/{id}/read - Should mark chat as read")
    @WithMockCustomUser()
    void markAsRead_ShouldReturnOk() throws Exception {
        // Given
        Long conversationId = 100L;

        doNothing().when(conversationService).markAsRead(conversationId, TEST_USER_ID);

        // When/Then
        mockMvc.perform(post("/api/chats/{id}/read", conversationId)
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(conversationService).markAsRead(conversationId, TEST_USER_ID);
    }

    @Test
    @DisplayName("PATCH /api/chats/{id}/mute - Should toggle mute status")
    @WithMockCustomUser()
    void mute_ShouldToggleMuteStatus() throws Exception {
        // Given
        Long conversationId = 100L;
        boolean muteStatus = true;

        doNothing().when(conversationService).toggleMute(TEST_USER_ID, conversationId, muteStatus);

        // When/Then
        mockMvc.perform(patch("/api/chats/{id}/mute", conversationId)
                        .param("status", String.valueOf(muteStatus))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(conversationService).toggleMute(TEST_USER_ID, conversationId, muteStatus);
    }

    @Test
    @DisplayName("GET /api/chats/{conversationId}/messages - Should get chat history with attachments")
    @WithMockCustomUser()
    void getChatHistory_ShouldReturnMessagesWithAttachments() throws Exception {
        // Given
        Long conversationId = 100L;
        int page = 0;
        int size = 50;

        // Create attachment DTOs with your actual structure
        AttachmentDTO attachment1 = new AttachmentDTO(
                1,                           // id
                "storage-uuid-123.jpg",      // storageName
                "vacation-photo.jpg",        // originalName
                "image/jpeg",                 // contentType
                1024L,                        // fileSize
                "/thumbnails/123.jpg",        // thumbnailUrl
                "user1",                      // uploadedBy
                1L,                           // messageId
                conversationId                 // conversationId
        );

        AttachmentDTO attachment2 = new AttachmentDTO(
                2,
                "storage-uuid-456.pdf",
                "document.pdf",
                "application/pdf",
                2048L,
                null,                          // thumbnailUrl can be null for PDFs
                "user1",
                3L,
                conversationId
        );

        // Create messages with attachments using the correct ChatMessageResponse record
        List<ChatMessageResponse> expectedMessages = List.of(
                new ChatMessageResponse(
                        1L,
                        "Hello with attachment",
                        Instant.now().minusSeconds(3600),
                        "user1",
                        conversationId,
                        List.of(attachment1)  // Message with one attachment
                ),
                new ChatMessageResponse(
                        2L,
                        "Just text message",
                        Instant.now(),
                        "user2",
                        conversationId,
                        List.of()  // Message with no attachments
                ),
                new ChatMessageResponse(
                        3L,
                        "Multiple attachments",
                        Instant.now().minusSeconds(1800),
                        "user1",
                        conversationId,
                        List.of(attachment1, attachment2)  // Message with two attachments
                )
        );

        when(messageService.getChatHistory(eq(conversationId), eq(page), eq(size), eq(TEST_USER_ID)))
                .thenReturn(expectedMessages);

        // When/Then
        mockMvc.perform(get("/api/chats/{conversationId}/messages", conversationId)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                // Check first message
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].content").value("Hello with attachment"))
                .andExpect(jsonPath("$[0].senderUsername").value("user1"))
                .andExpect(jsonPath("$[0].conversationId").value(conversationId))
                .andExpect(jsonPath("$[0].attachments.length()").value(1))
                .andExpect(jsonPath("$[0].attachments[0].id").value(1))
                .andExpect(jsonPath("$[0].attachments[0].storageName").value("storage-uuid-123.jpg"))
                .andExpect(jsonPath("$[0].attachments[0].originalName").value("vacation-photo.jpg"))
                .andExpect(jsonPath("$[0].attachments[0].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$[0].attachments[0].fileSize").value(1024))
                .andExpect(jsonPath("$[0].attachments[0].thumbnailUrl").value("/thumbnails/123.jpg"))
                .andExpect(jsonPath("$[0].attachments[0].uploadedBy").value("user1"))
                .andExpect(jsonPath("$[0].attachments[0].messageId").value(1L))
                .andExpect(jsonPath("$[0].attachments[0].conversationId").value(conversationId))

                // Check second message (no attachments)
                .andExpect(jsonPath("$[1].id").value(2L))
                .andExpect(jsonPath("$[1].content").value("Just text message"))
                .andExpect(jsonPath("$[1].senderUsername").value("user2"))
                .andExpect(jsonPath("$[1].attachments.length()").value(0))

                // Check third message (multiple attachments)
                .andExpect(jsonPath("$[2].id").value(3L))
                .andExpect(jsonPath("$[2].content").value("Multiple attachments"))
                .andExpect(jsonPath("$[2].attachments.length()").value(2))
                .andExpect(jsonPath("$[2].attachments[0].id").value(1))
                .andExpect(jsonPath("$[2].attachments[0].originalName").value("vacation-photo.jpg"))
                .andExpect(jsonPath("$[2].attachments[1].id").value(2))
                .andExpect(jsonPath("$[2].attachments[1].originalName").value("document.pdf"))
                .andExpect(jsonPath("$[2].attachments[1].thumbnailUrl").isEmpty())

                .andExpect(jsonPath("$.length()").value(3));

        verify(messageService).getChatHistory(eq(conversationId), eq(page), eq(size), eq(TEST_USER_ID));
    }

    @Test
    @DisplayName("GET /api/chats/{conversationId}/messages - Should handle empty message list")
    @WithMockCustomUser()
    void getChatHistory_WithNoMessages_ShouldReturnEmptyList() throws Exception {
        // Given
        Long conversationId = 100L;

        when(messageService.getChatHistory(eq(conversationId), eq(0), eq(50), eq(TEST_USER_ID)))
                .thenReturn(List.of());

        // When/Then
        mockMvc.perform(get("/api/chats/{conversationId}/messages", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(messageService).getChatHistory(eq(conversationId), eq(0), eq(50), eq(TEST_USER_ID));
    }

    @Test
    @DisplayName("GET /api/chats/{conversationId}/messages - Should use default pagination")
    @WithMockCustomUser()
    void getChatHistory_WithDefaultPagination_ShouldUseDefaults() throws Exception {
        // Given
        Long conversationId = 100L;

        when(messageService.getChatHistory(eq(conversationId), eq(0), eq(50), eq(TEST_USER_ID)))
                .thenReturn(List.of());

        // When/Then
        mockMvc.perform(get("/api/chats/{conversationId}/messages", conversationId))
                .andExpect(status().isOk());

        verify(messageService).getChatHistory(eq(conversationId), eq(0), eq(50), eq(TEST_USER_ID));
    }

    @Test
    @DisplayName("GET /api/chats/{conversationId}/messages - Should handle custom pagination")
    @WithMockCustomUser()
    void getChatHistory_WithCustomPagination_ShouldUseCustomValues() throws Exception {
        // Given
        Long conversationId = 100L;
        int page = 2;
        int size = 20;

        when(messageService.getChatHistory(eq(conversationId), eq(page), eq(size), eq(TEST_USER_ID)))
                .thenReturn(List.of());

        // When/Then
        mockMvc.perform(get("/api/chats/{conversationId}/messages", conversationId)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(status().isOk());

        verify(messageService).getChatHistory(eq(conversationId), eq(page), eq(size), eq(TEST_USER_ID));
    }

    @Test
    @DisplayName("All endpoints should return 401 without authentication")
    void endpoints_WithoutAuth_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/chats/direct").with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/chats/group").with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/chats/my"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/chats/1/read").with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/chats/1/mute").with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/chats/1/messages"))
                .andExpect(status().isUnauthorized());
    }
}
