//package com.example.WebChat;
//
//
//import com.example.WebChat.DTO.*;
//import com.example.WebChat.Service.ConversationService;
//import com.example.WebChat.Service.MessageService;
//import com.example.WebChat.Service.RateLimiterService;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.http.MediaType;
//import org.springframework.security.test.context.support.WithMockUser;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.time.Instant;
//import java.util.List;
//
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@SpringBootTest
//@AutoConfigureMockMvc
//class ConversationControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @MockitoBean
//    private ConversationService conversationService;
//
//    @MockitoBean
//    private MessageService messageService;
//
//    @MockitoBean
//    private RateLimiterService rateLimiterService;
//
//    private static final Long TEST_USER_ID = 1L;
//    private static final String TEST_USERNAME = "testuser";
//
//    // ==================== DIRECT CHAT TESTS ====================
//
//    @Test
//    @DisplayName("POST /api/chats/direct - Should open direct chat")
//    @WithMockUser(username = TEST_USERNAME, authorities = {"USER"})
//    void openDirectChat_ShouldReturnConversation() throws Exception {
//        OpenDirectChatRequest request = new OpenDirectChatRequest(1L, 2L);
//        ConversationResponse expectedResponse = new ConversationResponse(
//                100L, "otheruser", "avatar.png", "Hello", 0, Instant.now()
//        );
//
//        when(conversationService.openDirectChatPreview(1L, 2L)).thenReturn(expectedResponse);
//
//        mockMvc.perform(post("/api/chats/direct")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.id").value(100L))
//                .andExpect(jsonPath("$.name").value("otheruser"))
//                .andExpect(jsonPath("$.lastMessage").value("Hello"));
//
//        verify(conversationService).openDirectChatPreview(1L, 2L);
//    }
//
//    // ==================== GROUP CHAT TESTS ====================
//
//    @Test
//    @DisplayName("POST /api/chats/group - Should create group chat")
//    @WithMockUser(username = TEST_USERNAME, authorities = {"USER"})
//    void createGroup_ShouldReturnGroupConversation() throws Exception {
//        OpenGroupChatRequest request = new OpenGroupChatRequest(
//                "Test Group",
//                List.of(2L, 3L, 4L)
//        );
//
//        ConversationResponse expectedResponse = new ConversationResponse(
//                200L, "Test Group", "group-avatar.png", "Welcome!", 0, Instant.now()
//        );
//
//        when(conversationService.createGroupChatPreview(any(OpenGroupChatRequest.class), eq(TEST_USER_ID)))
//                .thenReturn(expectedResponse);
//
//        mockMvc.perform(post("/api/chats/group")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.id").value(200L))
//                .andExpect(jsonPath("$.name").value("Test Group"));
//
//        verify(conversationService).createGroupChatPreview(any(OpenGroupChatRequest.class), eq(TEST_USER_ID));
//    }
//
//    // ==================== GET CHATS TESTS ====================
//
//    @Test
//    @DisplayName("GET /api/chats/my - Should return user's chats")
//    @WithMockUser(username = TEST_USERNAME, authorities = {"USER"})
//    void getMyChats_ShouldReturnListOfConversations() throws Exception {
//        List<ConversationResponse> expectedChats = List.of(
//                new ConversationResponse(1L, "Chat1", "avatar1.png", "Hi", 2, Instant.now()),
//                new ConversationResponse(2L, "Chat2", "avatar2.png", "Hello", 0, Instant.now())
//        );
//
//        when(conversationService.getUserChats(TEST_USER_ID)).thenReturn(expectedChats);
//
//        mockMvc.perform(get("/api/chats/my"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$[0].id").value(1L))
//                .andExpect(jsonPath("$[0].name").value("Chat1"))
//                .andExpect(jsonPath("$[1].id").value(2L))
//                .andExpect(jsonPath("$.length()").value(2));
//
//        verify(conversationService).getUserChats(TEST_USER_ID);
//    }
//
//    // ==================== MARK AS READ TESTS ====================
//
//
//    // ==================== MUTE TOGGLE TESTS ====================
//
//    @Test
//    @DisplayName("PATCH /api/chats/{id}/mute - Should toggle mute status")
//    @WithMockUser(username = TEST_USERNAME, authorities = {"USER"})
//    void mute_ShouldToggleMuteStatus() throws Exception {
//        Long conversationId = 100L;
//        boolean muteStatus = true;
//
//        doNothing().when(conversationService).toggleMute(TEST_USER_ID, conversationId, muteStatus);
//
//        mockMvc.perform(patch("/api/chats/{id}/mute", conversationId)
//                        .param("status", String.valueOf(muteStatus)))
//                .andExpect(status().isOk());
//
//        verify(conversationService).toggleMute(TEST_USER_ID, conversationId, muteStatus);
//    }
//
//    // ==================== MESSAGE HISTORY TESTS ====================
//
//    @Test
//    @DisplayName("GET /api/chats/{conversationId}/messages - Should get chat history")
//    @WithMockUser(username = TEST_USERNAME, authorities = {"USER"})
//    void getChatHistory_ShouldReturnMessages() throws Exception {
//        Long conversationId = 100L;
//        int page = 0;
//        int size = 50;
//
//        List<ChatMessageResponse> expectedMessages = List.of(
//                new ChatMessageResponse(
//                        1L,
//                        "Hello",
//                        Instant.now(),
//                        "user1",
//                        conversationId,
//                        List.of()
//                )
//        );
//
//        when(messageService.getChatHistory(eq(conversationId), eq(page), eq(size), eq(TEST_USER_ID)))
//                .thenReturn(expectedMessages);
//
//        mockMvc.perform(get("/api/chats/{conversationId}/messages", conversationId)
//                        .param("page", String.valueOf(page))
//                        .param("size", String.valueOf(size)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.length()").value(1));
//
//        verify(messageService).getChatHistory(eq(conversationId), eq(page), eq(size), eq(TEST_USER_ID));
//    }
//
//    @Test
//    @DisplayName("GET /api/chats/{conversationId}/messages - Should handle empty list")
//    @WithMockUser(username = TEST_USERNAME, authorities = {"USER"})
//    void getChatHistory_WithNoMessages_ShouldReturnEmptyList() throws Exception {
//        Long conversationId = 100L;
//
//        when(messageService.getChatHistory(eq(conversationId), eq(0), eq(50), eq(TEST_USER_ID)))
//                .thenReturn(List.of());
//
//        mockMvc.perform(get("/api/chats/{conversationId}/messages", conversationId))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.length()").value(0));
//
//        verify(messageService).getChatHistory(eq(conversationId), eq(0), eq(50), eq(TEST_USER_ID));
//    }
//}