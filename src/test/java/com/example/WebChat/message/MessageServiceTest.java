package com.example.WebChat.message;

import com.example.WebChat.config.RabbitMQConfig;
import com.example.WebChat.conversation.MembershipGuard;
import com.example.WebChat.message.dto.ChatMessageEvent;
import com.example.WebChat.message.dto.WatermarkUpdateEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    private static final Long USER_ID = 5L;
    private static final Long CONVERSATION_ID = 6L;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private MembershipGuard membershipGuard;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private org.springframework.data.redis.core.SetOperations<String, String> setOperations;

    @Mock
    private com.example.WebChat.config.AppProperties appProperties;

    @Mock
    private com.example.WebChat.conversation.ConversationMembersCacheService conversationMembersCacheService;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private MessageService messageService;

    @Test
    @DisplayName("processAndSend broadcasts immediately and publishes async event for members")
    void processAndSend_broadcastsAndPublishes() {
        when(membershipGuard.isMember(USER_ID, CONVERSATION_ID)).thenReturn(true);

        messageService.processAndSend(USER_ID, CONVERSATION_ID, "hello", "temp-1", null);

        ArgumentCaptor<ChatMessageEvent> eventCaptor = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/chat/" + CONVERSATION_ID), eventCaptor.capture());
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.CHAT_EXCHANGE),
                eq(RabbitMQConfig.CHAT_ROUTING_KEY),
                eq(eventCaptor.getValue())
        );

        ChatMessageEvent event = eventCaptor.getValue();
        assertThat(event.tempId()).isEqualTo("temp-1");
        assertThat(event.content()).isEqualTo("hello");
        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(event.sentAt()).isNotNull();
        assertThat(event.replyToMessageId()).isNull();
    }

    @Test
    @DisplayName("processAndSend rejects non-members before any side effects")
    void processAndSend_rejectsNonMember() {
        when(membershipGuard.isMember(USER_ID, CONVERSATION_ID)).thenReturn(false);

        assertThatThrownBy(() -> messageService.processAndSend(USER_ID, CONVERSATION_ID, "hello", "temp-1", null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not a member");

        verifyNoInteractions(messagingTemplate, rabbitTemplate);
    }

    @Test
    @DisplayName("handleMessageAck publishes watermark and caches the advanced position")
    void handleMessageAck_publishesAndCaches() {
        when(membershipGuard.isMember(USER_ID, CONVERSATION_ID)).thenReturn(true);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        com.example.WebChat.config.AppProperties.Watermarks watermarks = new com.example.WebChat.config.AppProperties.Watermarks();
        watermarks.setDeliveredReceiptMemberLimit(50);
        org.mockito.Mockito.lenient().when(appProperties.getWatermarks()).thenReturn(watermarks);

        WatermarkUpdateEvent event = new WatermarkUpdateEvent(CONVERSATION_ID, USER_ID, 99L, "READ");

        messageService.handleMessageAck(event);

        verify(hashOperations).put("watermarks:6:5", "read", "99");
    }

    @Test
    @DisplayName("handleMessageAck rejects non-members and does not touch Rabbit or Redis")
    void handleMessageAck_rejectsNonMember() {
        when(membershipGuard.isMember(USER_ID, CONVERSATION_ID)).thenReturn(false);

        assertThatThrownBy(() -> messageService.handleMessageAck(
                new WatermarkUpdateEvent(CONVERSATION_ID, USER_ID, 99L, "READ")
        )).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(rabbitTemplate);
        verify(stringRedisTemplate, never()).opsForHash();
    }
}
