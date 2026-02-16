package com.example.WebChat;

import com.example.WebChat.DTO.ChatMessageEvent;
import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Entity.Message;
import com.example.WebChat.Entity.User;

import com.example.WebChat.Repository.ConvMembershipRepository;
import com.example.WebChat.Repository.MessageRepository;
import com.example.WebChat.Service.MessageService;
import com.example.WebChat.UtilsConfigs.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock MessageRepository messageRepository;
    @Mock ConvMembershipRepository convMembershipRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock RabbitTemplate rabbitTemplate;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ObjectMapper objectMapper;

    @Mock ListOperations<String, String> listOps;
    @Mock Bucket bucket;

    @InjectMocks MessageService messageService;

    @Captor ArgumentCaptor<ChatMessageEvent> eventCaptor;
    @Captor ArgumentCaptor<Message> messageCaptor;

    private static final Long USER_ID = 1L;
    private static final String USERNAME = "testuser";
    private static final Long CONVERSATION_ID = 100L;
    private static final String CONTENT = "Hello, world!";
    private static final String TEMP_ID = "temp-123-abc";

    private User user;
    private Conversation conversation;

    private String historyKey() {
        return "chat:history:" + CONVERSATION_ID;
    }

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).username(USERNAME).build();
        conversation = Conversation.builder().conversationID(CONVERSATION_ID).isGroup(false).build();

        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
    }

    /* =========================================================
       processAndSend
       ========================================================= */

    @Nested
    @DisplayName("processAndSend()")
    class ProcessAndSendTests {

        @Test
        @DisplayName("Happy path: broadcasts WS + publishes RabbitMQ")
        void shouldProcessAndSend() {
            when(convMembershipRepository.existsByUserIdAndConvId(USER_ID, CONVERSATION_ID)).thenReturn(true);


            messageService.processAndSend(USER_ID, USERNAME, CONVERSATION_ID, CONTENT, TEMP_ID);

            // WS
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/chat/" + CONVERSATION_ID),
                    any(ChatMessageEvent.class)
            );

            // Rabbit
            verify(rabbitTemplate).convertAndSend(
                    eq(RabbitMQConfig.CHAT_EXCHANGE),
                    eq(RabbitMQConfig.CHAT_ROUTING_KEY),
                    eventCaptor.capture()
            );

            ChatMessageEvent e = eventCaptor.getValue();
            assertThat(e.tempId()).isEqualTo(TEMP_ID);
            assertThat(e.content()).isEqualTo(CONTENT);
            assertThat(e.senderName()).isEqualTo(USERNAME);
            assertThat(e.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(e.sentAt()).isNotNull();

            verifyNoMoreInteractions(rabbitTemplate, messagingTemplate);
        }

        @Test
        @DisplayName("Throws AccessDeniedException when user not member; no WS/Rabbit")
        void shouldThrowForbiddenWhenNotMember() {
            when(convMembershipRepository.existsByUserIdAndConvId(USER_ID, CONVERSATION_ID)).thenReturn(false);

            assertThatThrownBy(() ->
                    messageService.processAndSend(USER_ID, USERNAME, CONVERSATION_ID, CONTENT, TEMP_ID)
            ).isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("not a member");

            verifyNoInteractions( messagingTemplate, rabbitTemplate);
        }


        @Test
        @DisplayName("Order: WS broadcast happens before Rabbit publish")
        void shouldBroadcastBeforeRabbit() {
            when(convMembershipRepository.existsByUserIdAndConvId(USER_ID, CONVERSATION_ID)).thenReturn(true);



            messageService.processAndSend(USER_ID, USERNAME, CONVERSATION_ID, CONTENT, TEMP_ID);

            InOrder inOrder = inOrder(messagingTemplate, rabbitTemplate);

            inOrder.verify(messagingTemplate).convertAndSend(
                    eq("/topic/chat/" + CONVERSATION_ID),
                    any(ChatMessageEvent.class)
            );

            inOrder.verify(rabbitTemplate).convertAndSend(
                    eq(RabbitMQConfig.CHAT_EXCHANGE),
                    eq(RabbitMQConfig.CHAT_ROUTING_KEY),
                    any(ChatMessageEvent.class)
            );
        }
    }

    /* =========================================================
       getChatHistory
       ========================================================= */

    @Nested
    @DisplayName("getChatHistory()")
    class GetChatHistoryTests {

        @Test
        @DisplayName("Redis HIT for cached page within first 100 messages")
        void shouldReturnFromRedisHit() throws Exception {
            when(convMembershipRepository.existsByUserIdAndConvId(USER_ID, CONVERSATION_ID)).thenReturn(true);

            // page=0 size=20 => start 0 end 19
            when(listOps.size(historyKey())).thenReturn(50L);

            List<String> cachedJson = List.of(
                    "{\"id\":1,\"content\":\"Hello\"}",
                    "{\"id\":2,\"content\":\"World\"}"
            );
            when(listOps.range(historyKey(), 0, 19)).thenReturn(cachedJson);

            ChatMessageResponse r1 = mock(ChatMessageResponse.class);
            ChatMessageResponse r2 = mock(ChatMessageResponse.class);

            when(objectMapper.readValue(cachedJson.get(0), ChatMessageResponse.class)).thenReturn(r1);
            when(objectMapper.readValue(cachedJson.get(1), ChatMessageResponse.class)).thenReturn(r2);

            List<ChatMessageResponse> out = messageService.getChatHistory(CONVERSATION_ID, 0, 20, USER_ID);

            assertThat(out).containsExactly(r1, r2);
            verifyNoInteractions(messageRepository);
        }

        @Test
        @DisplayName("Redis returns bad JSON entries -> deserialize filters nulls")
        void shouldFilterBadJsonFromRedis() throws Exception {
            when(convMembershipRepository.existsByUserIdAndConvId(USER_ID, CONVERSATION_ID)).thenReturn(true);
            when(listOps.size(historyKey())).thenReturn(50L);

            List<String> cachedJson = List.of("{bad}", "{\"id\":2}");
            when(listOps.range(historyKey(), 0, 19)).thenReturn(cachedJson);

            ChatMessageResponse ok = mock(ChatMessageResponse.class);
            when(objectMapper.readValue("{bad}", ChatMessageResponse.class)).thenThrow(new RuntimeException("boom"));
            when(objectMapper.readValue("{\"id\":2}", ChatMessageResponse.class)).thenReturn(ok);

            List<ChatMessageResponse> out = messageService.getChatHistory(CONVERSATION_ID, 0, 20, USER_ID);

            assertThat(out).containsExactly(ok);
            verifyNoInteractions(messageRepository);
        }

        @Test
        @DisplayName("Page 0 Redis MISS -> warms up 100 from DB, refreshes Redis, returns first 'size'")
        void shouldWarmUpCacheOnFirstPageMiss() throws Exception {
            when(convMembershipRepository.existsByUserIdAndConvId(USER_ID, CONVERSATION_ID)).thenReturn(true);

            // MISS: either size is 0 or not enough
            when(listOps.size(historyKey())).thenReturn(0L);

            // DB warmup IDs
            Pageable warmUpPageable = PageRequest.of(0, 100, Sort.by("sentAt").descending());
            @SuppressWarnings("unchecked")
            Slice<Long> idsSlice = mock(Slice.class);
            when(idsSlice.isEmpty()).thenReturn(false);
            when(idsSlice.getContent()).thenReturn(List.of(11L, 22L, 33L));
            when(messageRepository.findMessageIds(CONVERSATION_ID, warmUpPageable)).thenReturn(idsSlice);

            // DB messages
            Message m1 = mock(Message.class);
            Message m2 = mock(Message.class);
            Message m3 = mock(Message.class);
            when(messageRepository.findMessagesWithDetails(List.of(11L, 22L, 33L)))
                    .thenReturn(List.of(m1, m2, m3));

            // fromEntity is static -> needs mockStatic
            ChatMessageResponse r1 = mock(ChatMessageResponse.class);
            ChatMessageResponse r2 = mock(ChatMessageResponse.class);
            ChatMessageResponse r3 = mock(ChatMessageResponse.class);

            try (MockedStatic<ChatMessageResponse> mocked = mockStatic(ChatMessageResponse.class)) {
                mocked.when(() -> ChatMessageResponse.fromEntity(m1)).thenReturn(r1);
                mocked.when(() -> ChatMessageResponse.fromEntity(m2)).thenReturn(r2);
                mocked.when(() -> ChatMessageResponse.fromEntity(m3)).thenReturn(r3);

                // serialization for redis
                when(objectMapper.writeValueAsString(any(ChatMessageResponse.class))).thenReturn("json");

                List<ChatMessageResponse> out = messageService.getChatHistory(CONVERSATION_ID, 0, 2, USER_ID);

                // returns first "size" only => 2
                assertThat(out).containsExactly(r1, r2);
            }

            verify(redisTemplate).delete(historyKey());
            verify(listOps).rightPushAll(eq(historyKey()), anyList());
            verify(listOps).trim(historyKey(), 0, 99);
            verify(redisTemplate).expire(eq(historyKey()), any(Duration.class));
        }

        @Test
        @DisplayName("Page >0 Redis miss -> DB read for that page")
        void shouldFetchFromDbForPagesBeyondRedisOrMiss() {
            when(convMembershipRepository.existsByUserIdAndConvId(USER_ID, CONVERSATION_ID)).thenReturn(true);

            // Force Redis check to happen, but return empty so it falls back to DB
            when(listOps.size(historyKey())).thenReturn(60L);            // 60 > end(59) so range() will be called
            when(listOps.range(historyKey(), 40, 59)).thenReturn(List.of()); // empty => miss => DB path

            Pageable pageable = PageRequest.of(2, 20, Sort.by("sentAt").descending());

            @SuppressWarnings("unchecked")
            Slice<Long> idsSlice = mock(Slice.class);
            when(idsSlice.getContent()).thenReturn(List.of(5L, 6L, 7L));
            when(messageRepository.findMessageIds(CONVERSATION_ID, pageable)).thenReturn(idsSlice);

            Message m5 = mock(Message.class);
            Message m6 = mock(Message.class);
            Message m7 = mock(Message.class);
            when(messageRepository.findMessagesWithDetails(List.of(5L, 6L, 7L)))
                    .thenReturn(List.of(m5, m6, m7));

            ChatMessageResponse r5 = mock(ChatMessageResponse.class);
            ChatMessageResponse r6 = mock(ChatMessageResponse.class);
            ChatMessageResponse r7 = mock(ChatMessageResponse.class);

            try (MockedStatic<ChatMessageResponse> mocked = mockStatic(ChatMessageResponse.class)) {
                mocked.when(() -> ChatMessageResponse.fromEntity(m5)).thenReturn(r5);
                mocked.when(() -> ChatMessageResponse.fromEntity(m6)).thenReturn(r6);
                mocked.when(() -> ChatMessageResponse.fromEntity(m7)).thenReturn(r7);

                List<ChatMessageResponse> out = messageService.getChatHistory(CONVERSATION_ID, 2, 20, USER_ID);
                assertThat(out).containsExactly(r5, r6, r7);
            }
        }


        @Test
        @DisplayName("Throws FORBIDDEN when user not member")
        void shouldThrowForbiddenWhenNotMember() {
            when(convMembershipRepository.existsByUserIdAndConvId(USER_ID, CONVERSATION_ID)).thenReturn(false);

            assertThatThrownBy(() ->
                    messageService.getChatHistory(CONVERSATION_ID, 0, 20, USER_ID)
            ).isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("403");
        }

        @Test
        @DisplayName("Page 0 warmup: DB returns empty => empty list")
        void shouldReturnEmptyWhenNoMessages() {
            when(convMembershipRepository.existsByUserIdAndConvId(USER_ID, CONVERSATION_ID)).thenReturn(true);
            when(listOps.size(historyKey())).thenReturn(0L);

            Pageable warmUpPageable = PageRequest.of(0, 100, Sort.by("sentAt").descending());
            @SuppressWarnings("unchecked")
            Slice<Long> emptySlice = mock(Slice.class);
            when(emptySlice.isEmpty()).thenReturn(true);

            when(messageRepository.findMessageIds(CONVERSATION_ID, warmUpPageable)).thenReturn(emptySlice);

            List<ChatMessageResponse> out = messageService.getChatHistory(CONVERSATION_ID, 0, 20, USER_ID);

            assertThat(out).isEmpty();
            verify(messageRepository, never()).findMessagesWithDetails(anyList());
        }
    }

    /* =========================================================
       create()
       ========================================================= */

    @Nested
    @DisplayName("create()")
    class CreateTests {

    /* =========================================================
       deserialize()
       ========================================================= */

    @Nested
    @DisplayName("deserialize()")
    class DeserializeTests {

        @Test
        void shouldDeserializeJson() throws Exception {
            String json = "{\"id\":1}";
            ChatMessageResponse expected = mock(ChatMessageResponse.class);
            when(objectMapper.readValue(json, ChatMessageResponse.class)).thenReturn(expected);

            ChatMessageResponse out = messageService.deserialize(json);

            assertThat(out).isEqualTo(expected);
        }

        @Test
        void shouldReturnNullOnError() throws Exception {
            String json = "{bad}";
            when(objectMapper.readValue(json, ChatMessageResponse.class))
                    .thenThrow(new RuntimeException("boom"));

            ChatMessageResponse out = messageService.deserialize(json);

            assertThat(out).isNull();
        }
    }
}
    }
