package com.example.WebChat;

import com.example.WebChat.DTO.ChatMessageEvent;
import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Entity.Message;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Repository.*;
import com.example.WebChat.Service.MessageService;
import com.example.WebChat.Service.RateLimiterService;
import com.example.WebChat.UtilsConfigs.RabbitMQConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.example.WebChat.Repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

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

    @Mock private UserRepository userRepository;
    @Mock private ConvMembershipRepository convMembershipRepository;
    @Mock private RateLimiterService rateLimiter;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private RabbitTemplate rabbitTemplate;

    // Use StringRedisTemplate to match the Service type exactly
    @Mock private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    @Mock private ListOperations<String, String> listOps;

    // We need this to handle the JSON conversion in the 'Redis Hit' test
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock private Bucket bucket;


    @BeforeEach
    void setUp() {
        // Clear context before each test to be safe
        SecurityContextHolder.clearContext();
    }
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }


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
    void shouldReturnChatHistoryFromRedisAndAvoidRepo() throws Exception {
        // 1. Arrange
        Long convId = 1L;
        String username = "Mitsos";
        int size = 10;
        String historyKey = "chat:history:" + convId;

        // Security Context Setup
        Authentication auth = new UsernamePasswordAuthenticationToken(username, null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Membership Stub
        when(convMembershipRepository.existsByUser_UsernameAndConversation_ConversationID(username, convId))
                .thenReturn(true);

        // Redis Mocking
        when(redisTemplate.opsForList()).thenReturn(listOps);
        String jsonMessage = "{\"content\":\"Cached!\"}";
        when(listOps.range(historyKey, 0, size - 1)).thenReturn(List.of(jsonMessage));

        // Mock the Deserialization
        ChatMessageResponse cachedDto = new ChatMessageResponse("Cached!", Instant.now(), username, convId, List.of());
        when(objectMapper.readValue(jsonMessage, ChatMessageResponse.class)).thenReturn(cachedDto);

        // 2. Act
        List<ChatMessageResponse> result = messageService.getChatHistory(convId, 0, size);

        // 3. Assert
        assertEquals("Cached!", result.get(0).content());
        verifyNoInteractions(messageRepository); // Postgres was never touched!
    }

    @Test
    void shouldReturnChatHistoryFromRepoWhenRedisIsEmpty() {
        // 1. Arrange
        Long convId = 1L;
        String username = "Mitsos";
        int page = 0, size = 10;
        Pageable pageable = PageRequest.of(page, size, Sort.by("sentAt").descending());

        Authentication auth = new UsernamePasswordAuthenticationToken(username, null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(convMembershipRepository.existsByUser_UsernameAndConversation_ConversationID(username, convId))
                .thenReturn(true);

        // Mock Redis Miss
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.range("chat:history:" + convId, 0, size - 1)).thenReturn(List.of());

        // Mock DB Response
        User user = User.builder().username("Mitsos").email("G@m.com").id(1L).build();
        Conversation conv = Conversation.builder().conversationID(1L).build();
        Message entity = new Message();
        entity.setMessage("Hello world!");
        entity.setSentAt(Instant.now());
        entity.setSender(user);
        entity.setConversation(conv);
        ChatMessageResponse dto = new ChatMessageResponse("Hello world!", Instant.now(), username, convId, List.of());
        Slice<Message> mockSlice = new SliceImpl<>(List.of(entity), pageable, false);

        when(messageRepository.findByConversationIdOptimized(convId, pageable)).thenReturn(mockSlice);

        // 2. Act
        List<ChatMessageResponse> result = messageService.getChatHistory(convId, page, size);

        // 3. Assert
        assertEquals(1, result.size());
        assertEquals("Hello world!", result.get(0).content());
        verify(messageRepository).findByConversationIdOptimized(convId, pageable);
    }

    @Test
    void shouldProcessAndSendMessageSuccessfully() {
        // 1. Arrange
        String username = "Mitsos";
        Long convId = 1L;
        String content = "Hello!";
        String tempId = "uuid-123"; // The tracking number!

        User user = User.builder().id(10L).username(username).build();
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(convMembershipRepository.existsByUser_IdAndConversation_ConversationID(10L, convId)).thenReturn(true);
        when(rateLimiter.resolveMessageBucket(username)).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        // 2. Act
        // Notice: We don't expect a Response back yet because the DB hasn't seen it!
        messageService.processAndSend(username, convId, content, tempId);

        // 3. Assert (The WebSocket Broadcast)
        // We verify that the "Event" (the instant shout) was sent
        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/" + convId),
                argThat((ChatMessageEvent event) ->
                        event.tempId().equals(tempId) && event.content().equals(content)
                )
        );

        // 4. Assert (The RabbitMQ Handoff)
        // We verify the "Recipe" was sent to the background worker
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.CHAT_EXCHANGE),
                eq(RabbitMQConfig.CHAT_ROUTING_KEY),
                any(ChatMessageEvent.class)
        );

        // IMPORTANT: We REMOVE the verify for unread counts here.
        // Why? Because that happens in the Consumer test, not the Service test!
    }
}