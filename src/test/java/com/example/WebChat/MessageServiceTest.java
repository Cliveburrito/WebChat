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
    private MessageRepository messageRepository;

    @InjectMocks
    private MessageService messageService;

    @Mock private ConversationRepository conversationRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConvMembershipRepository convMembershipRepository;
    @Mock private RateLimiterService rateLimiter;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private Bucket bucket;

    @Test
    void shouldSaveMessageSuccessfully() {
        // 1. Arrange
        Message msg = Message.builder().message("Hello").build();
        when(messageRepository.save(any())).thenReturn(msg);

        // 2. Act
        Message saved = messageService.saveMessage(msg);

        // 3. Assert
        assertNotNull(saved);
        assertEquals("Hello", saved.getMessage());
        verify(messageRepository, times(1)).save(any());
    }

    @Test
    void shouldReturnMappedChatMessageResponsePage() {
        //  Arrange
        Long convId = 1L;
        Pageable pageable = PageRequest.of(0, 10);

        // Fake Sender
        User sender = User.builder().username("Mitsos").build();

        // Fake Message
        Message msg = new Message();
        msg.setMessage("Hello world!");
        msg.setSentAt(Instant.now());
        msg.setSender(sender);

        // Wrap the message in a page
        Page<Message> mockPage = new PageImpl<>(List.of(msg), pageable, 1);

        // Mock repository behavior
        when(messageRepository.findByConversationIdWithSender(eq(convId), eq(pageable)))
                .thenReturn(mockPage);

        // Call the method
        Page<ChatMessageResponse> result = messageService.getChatHistory(convId, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Mitsos", result.getContent().getFirst().senderUsername());
        assertEquals("Hello world!", result.getContent().getFirst().content());

        verify(messageRepository, times(1)).findByConversationIdWithSender(convId, pageable);
    }

    @Test
    void shouldProcessAndSendMessageSuccessfully() {
        // 1. Arrange
        String username = "Mitsos";
        Long convId = 1L;
        String content = "Hello!";

        User user = User.builder().id(10L).username(username).build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(convMembershipRepository.existsByUser_IdAndConversation_ConversationID(10L, convId)).thenReturn(true);
        when(rateLimiter.resolveMessageBucket(username)).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true); // Rate limit allows it
        when(convMembershipRepository.findUsernamesByConversationId(convId)).thenReturn(List.of("Mitsos", "Xenia"));

        // 2. Act
        ChatMessageResponse result = messageService.processAndSend(username, convId, content);

        // 3. Assert
        assertNotNull(result);
        assertEquals(content, result.content());

        // VERIFY: Did we broadcast to the main chat topic?
        verify(messagingTemplate).convertAndSend(eq("/topic/chat/" + convId), any(ChatMessageResponse.class));

        // VERIFY: Did we send 2 notifications (one for Mitsos, one for Xenia)?
        verify(messagingTemplate, times(2)).convertAndSend(startsWith("/topic/notifications/"), any(ChatMessageResponse.class));

        // VERIFY: Did we increment unread counts?
        verify(convMembershipRepository).incrementUnreadCountForOthers(convId, 10L);
    }
}