package com.example.WebChat.Service;

import com.example.WebChat.DTO.ChatMessageEvent;
import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Entity.Message;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Exception.RateLimitExceededException;
import com.example.WebChat.Repository.ConvMembershipRepository;
import com.example.WebChat.Repository.MessageRepository;
import com.example.WebChat.UtilsConfigs.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;
    private final ConvMembershipRepository convMembershipRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RateLimiterService rateLimiter;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Message create(User user, Conversation conversation, String content) {
        Message message = Message.builder()
                .sender(user)
                .sentAt(Instant.now())
                .conversation(conversation)
                .message(content)
                .build();

        //after saving in the db message will have it's generated id populated
        saveMessage(message);

        return message;
    }
    /**
     * Processes a message sent via WebSocket, saves it, and broadcasts it to the subscribers.
     */
    @Transactional
    public void processAndSend(Long userId, String username, Long conversationId, String content, String tempId) {
        boolean isMember = convMembershipRepository.existsByUserIdAndConvId(userId, conversationId);
        if (!isMember) throw new AccessDeniedException("You are not a member of this conversation.");

        Bucket bucket = rateLimiter.resolveMessageBucket(userId);
        if(!bucket.tryConsume(1)) {
            log.warn("User {} is spamming messages...",  username);
            throw new RateLimitExceededException("Too many messages!");
        }

        // 1. Create the lightweight event for the WebSocket
        ChatMessageEvent event = new ChatMessageEvent(
                tempId,
                content,
                username,
                conversationId,
                Instant.now()
        );

        // 2. Broadcast INSTANTLY via WebSocket (User A and B see the bubble now)
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, event);

        // 3. Hand off the "Hard Work" (DB Save) to RabbitMQ
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.CHAT_EXCHANGE,
                RabbitMQConfig.CHAT_ROUTING_KEY,
                event // Send the event with tempId so the Consumer knows it
        );

        log.info("Message handoff to RabbitMQ: tempId={}", tempId);
    }

    @Transactional
    public List<ChatMessageResponse> getChatHistory(Long conversationId, int page, int size, Long userId) {
        // 1. Security Check
        boolean isMember = convMembershipRepository.existsByUserIdAndConvId(userId, conversationId);
        if (!isMember) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member.");

        String historyKey = "chat:history:" + conversationId;

        // Υπολογίζουμε ποιο εύρος (range) ζητάει ο χρήστης
        int start = page * size;
        int end = start + size - 1;

        // 2. 🚀 THE BEAST RADIUS: Αν το αίτημα "χωράει" στα πρώτα 100 μηνύματα, κοίτα Redis
        if (end < 100) {
            Long currentLen = redisTemplate.opsForList().size(historyKey);

            // Αν ο Redis έχει αρκετά μηνύματα για να καλύψει το αίτημα
            if (currentLen != null && currentLen > end) {
                List<String> cached = redisTemplate.opsForList().range(historyKey, start, end);
                if (cached != null && !cached.isEmpty()) {
                    log.info("Redis Hit for Page {} - Conv {}", page, conversationId);
                    return cached.stream()
                            .map(this::deserialize)
                            .filter(java.util.Objects::nonNull)
                            .toList();
                }
            }
        }

        // 2. Αν έχουμε MISS στη σελίδα 0, κάνουμε "Heavy Warm Up"
        if (page == 0) {
            log.info("Postgres Cold Start (Warm Up 100) for Conv {}", conversationId);

            // Τραβάμε 100 IDs αντί για 'size'
            Pageable warmUpPageable = PageRequest.of(0, 100, Sort.by("sentAt").descending());
            Slice<Long> allIds = messageRepository.findMessageIds(conversationId, warmUpPageable);

            if (allIds.isEmpty()) return List.of();

            // Φέρνουμε τα πλήρη στοιχεία για τα 100
            List<Message> warmUpMessages = messageRepository.findMessagesWithDetails(allIds.getContent());
            List<ChatMessageResponse> allResponses = warmUpMessages.stream()
                    .map(ChatMessageResponse::fromEntity)
                    .toList();

            // Γεμίζουμε τον Redis με όλα (τα 100)
            refreshRedisCache(historyKey, allResponses);

            // Επιστρέφουμε στο UI μόνο όσα ζήτησε (π.χ. τα πρώτα 20)
            return allResponses.stream().limit(size).toList();
        }

        // 3. Για σελίδες > 0 που δεν υπήρχαν στον Redis (Cold Storage)
        log.info("Postgres Read for Page {} - Conv {}", page, conversationId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("sentAt").descending());
        Slice<Long> messageIdsSlice = messageRepository.findMessageIds(conversationId, pageable);
        List<Long> ids = messageIdsSlice.getContent();

        if (ids.isEmpty()) return List.of();

        List<Message> fullMessages = messageRepository.findMessagesWithDetails(ids);
        return fullMessages.stream()
                .map(ChatMessageResponse::fromEntity)
                .toList();
    }

    private void refreshRedisCache(String key, List<ChatMessageResponse> data) {
        try {
            redisTemplate.delete(key);

            // Μετατρέπουμε όλη τη λίστα σε JSON String List
            List<String> jsonList = data.stream()
                    .map(r -> {
                        try { return objectMapper.writeValueAsString(r); }
                        catch (Exception e) { return null; }
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();

            if (!jsonList.isEmpty()) {
                // 🚀 Μία κλήση, 100 μηνύματα. Τέλος.
                redisTemplate.opsForList().rightPushAll(key, jsonList);
                redisTemplate.opsForList().trim(key, 0, 99);
                redisTemplate.expire(key, Duration.ofDays(7));
            }
        } catch (Exception e) {
            log.warn("Redis warm up failed", e);
        }
    }

    @Async
    public void asyncWarmUp(Long conversationId, String historyKey) {
        log.info("Async warming up cache for conv {}", conversationId);
        // Τρέξε εδώ το βαρύ query των 100 και το refreshRedisCache
    }

    public Message saveMessage(Message message) {
        return messageRepository.save(message);
    }

    public ChatMessageResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, ChatMessageResponse.class);
        } catch (Exception e) {
            log.error("Failed to deserialize message from Redis", e);
            return null; // The .filter(Objects::nonNull) will clean this up
        }
    }
}
