package com.example.WebChat.Service;

import com.example.WebChat.DTO.ChatMessageEvent;
import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.DTO.WatermarkUpdateEvent;
import com.example.WebChat.Entity.Message;
import com.example.WebChat.Repository.ConvMembershipRepository;
import com.example.WebChat.Repository.MessageRepository;
import com.example.WebChat.UtilsConfigs.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;


import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;


@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MembershipGuard membershipGuard;
    private final ConvMembershipRepository convMembershipRepository;

    // Cache Keys
    private String getIndexKey(Long convId) { return "chat:index:" + convId; }
    private String getDataKey(Long convId) { return "chat:data:" + convId; }

    /**
     * This is the ENTRY POINT for every new message.
     * It broadcasts to WebSockets instantly and sends to RabbitMQ for persistence.
     */
    @Transactional
    public void processAndSend(Long userId, String username, Long conversationId, String content, String tempId) {
        // 1. Security check using our standalone Guard
        if (!membershipGuard.isMember(userId, conversationId)) {
            throw new AccessDeniedException("You are not a member of this conversation.");
        }

        // 2. Create the event object (The DTO the Consumer expects)
        ChatMessageEvent event = new ChatMessageEvent(
                tempId,
                content,
                username,
                conversationId,
                Instant.now()
        );

        // 3. OPTIMISTIC BROADCAST: Send via WebSocket NOW so users don't wait for DB
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, event);

        // 4. RABBITMQ HANDOFF: This is what triggers the Consumer
        // We send it to the EXCHANGE with a ROUTING_KEY
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.CHAT_EXCHANGE,
                RabbitMQConfig.CHAT_ROUTING_KEY,
                event
        );

        log.info("Message sent to RabbitMQ for async processing: tempId={}", tempId);
    }

    @Transactional
    public void handleMessageAck(WatermarkUpdateEvent wue) {
        Long userId = wue.userId();
        Long convId = wue.conversationId();
        Long msgId = wue.messageId();
        String type = wue.type();
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.CHAT_EXCHANGE,
                RabbitMQConfig.WATERMARK_ROUTING_KEY,
                wue

        );

        // 2. Update Redis Utility (for the snappy Sidebar/Status checks)
        String cacheKey = "watermarks:" + convId + ":" + userId;
        redisTemplate.opsForHash().put(cacheKey, type.toLowerCase(), msgId.toString());

        log.info("User {} marked messages up to {} as {} in chat {}", userId, msgId, type, convId);
    }

    @Transactional
    public List<ChatMessageResponse> getChatHistory(Long conversationId, int page, int size, Long userId) {
        // 1. Security Check
        if (!membershipGuard.isMember(userId, conversationId)) {
            throw new AccessDeniedException("You are not a member of this convo.");
        }

        String indexKey = getIndexKey(conversationId);
        String dataKey = getDataKey(conversationId);

        int start = page * size;
        int end = start + size - 1;

        // 2. Redis Lookup (ZSet + Hash)
        if (end < 100) {
            // Get IDs from Sorted Set (Reverse order for newest first)
            java.util.Set<String> messageIds = redisTemplate.opsForZSet().reverseRange(indexKey, start, end);

            if (messageIds != null && !messageIds.isEmpty()) {
                // Multi-get from Hash to avoid N+1 network calls
                List<Object> jsonList = redisTemplate.opsForHash().multiGet(dataKey, new java.util.ArrayList<>(messageIds));

                log.info("Redis Hash Hit for Page {} - Conv {}", page, conversationId);
                return jsonList.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(obj -> deserialize((String) obj))
                        .toList();
            }
        }

        // 3. Fallback: Postgres Warm Up (Page 0)
        if (page == 0) {
            log.info("Postgres Cold Start (Warm Up 100) for Conv {}", conversationId);
            Pageable warmUpPageable = PageRequest.of(0, 100, Sort.by("sentAt").descending());
            Slice<Long> allIds = messageRepository.findMessageIds(conversationId, warmUpPageable);

            if (allIds.isEmpty()) return List.of();

            List<Message> warmUpMessages = messageRepository.findMessagesWithDetails(allIds.getContent());
            List<ChatMessageResponse> allResponses = warmUpMessages.stream()
                    .map(ChatMessageResponse::fromEntity)
                    .toList();

            // Populate ZSet and Hash
            refreshRedisCache(conversationId, allResponses);
            return allResponses.stream().limit(size).toList();
        }

        // 4. Cold Storage (Deep pages)
        log.info("Postgres Read for Page {} - Conv {}", page, conversationId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("sentAt").descending());
        List<Long> ids = messageRepository.findMessageIds(conversationId, pageable).getContent();

        if (ids.isEmpty()) return List.of();
        return messageRepository.findMessagesWithDetails(ids).stream()
                .map(ChatMessageResponse::fromEntity)
                .toList();
    }

    /**
     * Surgically updates or adds a single message to the cache.
     * Used by the Consumer to keep the cache warm without clearing it.
     */
    public void updateMessageInCache(Long conversationId, ChatMessageResponse response) {
        String indexKey = getIndexKey(conversationId);
        String dataKey = getDataKey(conversationId);

        try {
            String json = objectMapper.writeValueAsString(response);
            String msgIdStr = response.id().toString();

            // Update Data (Hash) and Index (ZSet)
            redisTemplate.opsForHash().put(dataKey, msgIdStr, json);
            redisTemplate.opsForZSet().add(indexKey, msgIdStr, response.createdAt().toEpochMilli());

            // Garbage Collection: Keep only the 100 most recent items in index
            Long zSize = redisTemplate.opsForZSet().zCard(indexKey);
            if (zSize != null && zSize > 100) {
                long extra = zSize - 100;
                Set<String> toRemove = redisTemplate.opsForZSet().range(indexKey, 0, extra - 1);
                if (toRemove != null && !toRemove.isEmpty()) {
                    redisTemplate.opsForHash().delete(dataKey, toRemove.toArray());
                    redisTemplate.opsForZSet().remove(indexKey, toRemove.toArray());
                }
            }

            redisTemplate.expire(indexKey, Duration.ofDays(7));
            redisTemplate.expire(dataKey, Duration.ofDays(7));
        } catch (Exception e) {
            log.warn("Failed to surgically update cache for msg {}", response.id(), e);
        }
    }

    private void refreshRedisCache(Long convId, List<ChatMessageResponse> data) {
        String indexKey = getIndexKey(convId);
        String dataKey = getDataKey(convId);
        try {
            redisTemplate.delete(List.of(indexKey, dataKey));
            for (ChatMessageResponse r : data) {
                updateMessageInCache(convId, r);
            }
        } catch (Exception e) {
            log.warn("Redis warm up failed for conv {}", convId, e);
        }
    }

    // ... existing processAndSend and deserialize methods ...
    public ChatMessageResponse deserialize(String json) {
        try { return objectMapper.readValue(json, ChatMessageResponse.class); }
        catch (Exception e) { return null; }
    }
}