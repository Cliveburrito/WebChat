package com.example.WebChat;

import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Entity.Message;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Repository.*;
import com.example.WebChat.Service.MessageService;
import com.example.WebChat.Service.RateLimiterService;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.example.WebChat.Repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private MessageService messageService;


    @Mock private UserRepository userRepository;
    @Mock private ConvMembershipRepository convMembershipRepository;
    @Mock private RateLimiterService rateLimiter;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private Bucket bucket;

    @Test
    void shouldSaveMessageSuccessfully() {
        // Arrange
        Message msg = Message.builder().message("Hello").build();
        when(messageRepository.save(any())).thenReturn(msg);

        // Act
        Message saved = messageService.saveMessage(msg);

        // Assert
        assertNotNull(saved);
        assertEquals("Hello", saved.getMessage());
        verify(messageRepository, times(1)).save(any());
    }

    @Test
    void shouldReturnChatHistoryDirectlyFromRepo() {
        // 1. Arrange
        Long convId = 1L;
        String username = "Mitsos";
        Pageable pageable = PageRequest.of(0, 10);

        // 2. Χειροκίνητο SecurityContext (απαραίτητο για Unit Tests)
        Authentication auth = new UsernamePasswordAuthenticationToken(username, null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 3. Stub το membership check (για να μην πετάξει AccessDeniedException)
        when(convMembershipRepository.existsByUser_UsernameAndConversation_ConversationID(username, convId))
                .thenReturn(true);

        // 4. Mock το repository call
        ChatMessageResponse dto = new ChatMessageResponse("Hello world!", Instant.now(), username, convId);
        Page<ChatMessageResponse> mockPage = new PageImpl<>(List.of(dto), pageable, 1);

        when(messageRepository.findByConversationIdOptimized(convId, pageable))
                .thenReturn(mockPage);

        // 5. Act
        Page<ChatMessageResponse> result = messageService.getChatHistory(convId, pageable);

        // 6. Assert
        assertEquals(username, result.getContent().get(0).senderUsername());
        verify(messageRepository).findByConversationIdOptimized(convId, pageable);
    }

    @Test
    void shouldProcessAndSendMessageSuccessfully() {
        // Arrange
        String username = "Mitsos";
        Long convId = 1L;
        String content = "Hello!";

        User user = User.builder().id(10L).username(username).build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(convMembershipRepository.existsByUser_IdAndConversation_ConversationID(10L, convId)).thenReturn(true);
        when(rateLimiter.resolveMessageBucket(username)).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true); // Rate limit allows it
        when(convMembershipRepository.findUsernamesByConversationId(convId)).thenReturn(List.of("Mitsos", "Xenia"));

        // Act
        ChatMessageResponse result = messageService.processAndSend(username, convId, content);

        // Assert
        assertNotNull(result);
        assertEquals(content, result.content());

        // VERIFY: Did we broadcast to the main chat topic?
        verify(messagingTemplate).convertAndSend(eq("/topic/chat/" + convId), any(ChatMessageResponse.class));

        // VERIFY: Did we send 2 notifications ,one for mitsos, one for xenia?
        verify(messagingTemplate, times(2)).convertAndSend(startsWith("/topic/notifications/"), any(ChatMessageResponse.class));

        // VERIFY: Did we increment unread counts?
        verify(convMembershipRepository).incrementUnreadCountForOthers(convId, 10L);
    }
}