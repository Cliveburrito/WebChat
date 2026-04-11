package com.example.WebChat.message;

import com.example.WebChat.message.dto.ChatMessageEvent;
import com.example.WebChat.message.dto.ChatMessageResponse;
import com.example.WebChat.message.dto.MessageLifecycleEvent;
import com.example.WebChat.message.dto.WatermarkUpdateEvent;
import com.example.WebChat.config.AppProperties;
import com.example.WebChat.conversation.ConversationCacheService;
import com.example.WebChat.conversation.MembershipGuard;
import com.example.WebChat.config.RabbitMQConfig;
import com.example.WebChat.conversation.ConversationMembersCacheService;
import com.example.WebChat.shared.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

@Slf4j
@Service
public class MessageService {
    private final SimpMessagingTemplate messagingTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final MembershipGuard membershipGuard;
    private final ConversationMembersCacheService conversationMembersCacheService;
    private final ConversationCacheService conversationCacheService;
    private final AppProperties appProperties;
    private final MessageRepository messageRepository;
    private final MessageCacheService messageCacheService;
    static final String WATERMARK_KEY_PREFIX = "watermarks:";
    static final String DIRTY_WATERMARKS_KEY = "watermarks:dirty";

    @Autowired
    public MessageService(
            SimpMessagingTemplate messagingTemplate,
            RabbitTemplate rabbitTemplate,
            StringRedisTemplate stringRedisTemplate,
            MembershipGuard membershipGuard,
            ConversationMembersCacheService conversationMembersCacheService,
            ConversationCacheService conversationCacheService,
            AppProperties appProperties,
            MessageRepository messageRepository,
            MessageCacheService messageCacheService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.membershipGuard = membershipGuard;
        this.conversationMembersCacheService = conversationMembersCacheService;
        this.conversationCacheService = conversationCacheService;
        this.appProperties = appProperties;
        this.messageRepository = messageRepository;
        this.messageCacheService = messageCacheService;
    }

    public boolean canAccessConversation(Long userId, Long conversationId) {
        return membershipGuard.isMember(userId, conversationId);
    }

    @Transactional
    public ChatMessageResponse editMessage(Long userId, Long messageId, String content) {
        String normalizedContent = validateEditedContent(content);
        Message message = loadMessageForLifecycleChange(messageId);
        Long conversationId = message.getConversation().getId();

        assertCanChangeMessage(userId, message, "edit");
        if (message.isDeleted()) {
            throw new IllegalArgumentException("Deleted messages cannot be edited.");
        }

        message.setMessage(normalizedContent);
        message.setEditedAt(Instant.now());
        Message saved = messageRepository.save(message);
        ChatMessageResponse response = ChatMessageResponse.fromEntity(saved);

        afterCommitLifecycleUpdate(
                conversationId,
                response,
                new MessageLifecycleEvent(
                        MessageLifecycleEvent.MESSAGE_EDITED,
                        saved.getId(),
                        conversationId,
                        response.content(),
                        response.editedAt(),
                        null
                )
        );

        return response;
    }

    @Transactional
    public ChatMessageResponse deleteMessage(Long userId, Long messageId) {
        Message message = loadMessageForLifecycleChange(messageId);
        Long conversationId = message.getConversation().getId();

        assertCanChangeMessage(userId, message, "delete");
        if (message.getDeletedAt() == null) {
            message.setDeletedAt(Instant.now());
            messageRepository.save(message);
        }

        ChatMessageResponse response = ChatMessageResponse.fromEntity(message);
        afterCommitLifecycleUpdate(
                conversationId,
                response,
                new MessageLifecycleEvent(
                        MessageLifecycleEvent.MESSAGE_DELETED,
                        message.getId(),
                        conversationId,
                        null,
                        message.getEditedAt(),
                        response.deletedAt()
                )
        );

        return response;
    }

    // ----- Public API -----

    /**
     * Entry point for sending a new message.
     * We optimistically broadcast to WebSocket immediately, then publish to RabbitMQ for persistence.
     */
    public void processAndSend(Long userId, Long conversationId, String content, String tempId, Long replyToMessageId) {
        // Security check: only conversation members can send messages
        if (!membershipGuard.isMember(userId, conversationId)) {
            throw new AccessDeniedException("You are not a member of this conversation.");
        }

        Message replyTo = null;
        if (replyToMessageId != null) {
            replyTo = messageRepository.findByIdWithSenderAndConversation(replyToMessageId)
                    .filter(message -> message.getConversation().getId().equals(conversationId))
                    .orElseThrow(() -> new IllegalArgumentException("Reply target not found in conversation: " + replyToMessageId));
        }

        // Create the event object that the async consumer expects
        ChatMessageEvent event = new ChatMessageEvent(
                tempId,
                content,
                userId,
                conversationId,
                Instant.now(),
                replyToMessageId,
                replyTo == null ? null : replyTo.getSender().getUsername(),
                replyTo == null ? null : replyTo.isDeleted() ? "Message deleted" : replyTo.getMessage()
        );

        // Realtime broadcast (users see the message instantly)
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, event);

        // Async handoff for DB persistence and cache update (consumer)
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.CHAT_EXCHANGE,
                RabbitMQConfig.CHAT_ROUTING_KEY,
                event
        );

        log.info("Message sent to RabbitMQ for async processing: tempId={}", tempId);
    }

    /**
     * Handles watermark updates (read/delivered receipts).
     * Stored as String values in Redis for quick sidebar / status queries.
     */
    public boolean handleMessageAck(WatermarkUpdateEvent wue) {

        if (!membershipGuard.isMember(wue.userId(), wue.conversationId())) {
            throw new AccessDeniedException("You are not a member of this conversation.");
        }

        Long userId = wue.userId();
        Long convId = wue.conversationId();
        Long msgId  = wue.messageId();
        String type = normalizeWatermarkType(wue.type());

        if (msgId == null || msgId <= 0 || type == null) {
            return false;
        }

        if ("DELIVERED".equals(type) && shouldSkipDeliveredReceipt(convId)) {
            log.info("watermark_skip type=DELIVERED reason=large_conversation conversationId={} userId={} messageId={}", convId, userId, msgId);
            return false;
        }

        // Example key: watermarks:{convId}:{userId}
        String cacheKey = watermarkKey(convId, userId);
        String field = type.toLowerCase();
        Long previous = parseLong((String) stringRedisTemplate.opsForHash().get(cacheKey, field));

        if (previous != null && previous >= msgId) {
            return false;
        }

        stringRedisTemplate.opsForHash().put(cacheKey, field, msgId.toString());
        if ("READ".equals(type)) {
            Long previousDelivered = parseLong((String) stringRedisTemplate.opsForHash().get(cacheKey, "delivered"));
            if (previousDelivered == null || previousDelivered < msgId) {
                stringRedisTemplate.opsForHash().put(cacheKey, "delivered", msgId.toString());
            }
        }
        stringRedisTemplate.opsForSet().add(DIRTY_WATERMARKS_KEY, cacheKey);

        log.info("User {} marked messages up to {} as {} in chat {}", userId, msgId, type, convId);
        return true;
    }

    static String watermarkKey(Long conversationId, Long userId) {
        return WATERMARK_KEY_PREFIX + conversationId + ":" + userId;
    }

    private boolean shouldSkipDeliveredReceipt(Long conversationId) {
        int limit = appProperties.getWatermarks().getDeliveredReceiptMemberLimit();
        if (limit <= 0) {
            return false;
        }
        return conversationMembersCacheService.getMemberIds(conversationId).size() > limit;
    }

    private static String normalizeWatermarkType(String type) {
        if ("READ".equals(type) || "DELIVERED".equals(type)) {
            return type;
        }
        return null;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Message loadMessageForLifecycleChange(Long messageId) {
        return messageRepository.findByIdWithSenderAndConversation(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));
    }

    private void assertCanChangeMessage(Long userId, Message message, String action) {
        Long conversationId = message.getConversation().getId();
        if (!membershipGuard.isMember(userId, conversationId)) {
            throw new AccessDeniedException("You are not a member of this conversation.");
        }
        if (!message.getSender().getId().equals(userId)) {
            throw new AccessDeniedException("Only the message sender can " + action + " this message.");
        }
    }

    private String validateEditedContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Edited message content cannot be empty.");
        }
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("Edited message content cannot exceed 500 characters.");
        }
        return normalized;
    }

    private void afterCommitLifecycleUpdate(Long conversationId, ChatMessageResponse response, MessageLifecycleEvent event) {
        Runnable publish = () -> {
            messageCacheService.updateMessageInCache(conversationId, response);
            conversationCacheService.evictUserChats(conversationMembersCacheService.getMemberIds(conversationId));
            messagingTemplate.convertAndSend("/topic/chat/" + conversationId, event);
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
            return;
        }

        publish.run();
    }
}
