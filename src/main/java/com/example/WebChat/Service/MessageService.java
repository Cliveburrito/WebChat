package com.example.WebChat.Service;

import com.example.WebChat.DTO.ChatMessageEvent;
import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Entity.Message;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Exception.RateLimitExceededException;
import com.example.WebChat.Exception.ResourceNotFoundException;
import com.example.WebChat.Repository.ConvMembershipRepository;
import com.example.WebChat.Repository.MessageRepository;
import com.example.WebChat.Repository.UserRepository;
import com.example.WebChat.UtilsConfigs.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
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
    public void processAndSend(String username, Long conversationId, String content, String tempId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        boolean isMember = convMembershipRepository.existsByUser_IdAndConversation_ConversationID(user.getId(), conversationId);
        if (!isMember) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this conversation.");

        Bucket bucket = rateLimiter.resolveMessageBucket(username);
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
    public List<ChatMessageResponse> getChatHistory(Long conversationId, int page, int size) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // 1. Security Check
        if (!convMembershipRepository.existsByUser_UsernameAndConversation_ConversationID(currentUsername, conversationId)) {
            throw new AccessDeniedException("Access Denied");
        }

//        // 2. Page 0 = The "Hot" Page (Try Redis)
//        if (page == 0) {
//            String historyKey = "chat:history:" + conversationId;
//            List<String> cached = redisTemplate.opsForList().range(historyKey, 0, size - 1);
//
//            if (cached != null && !cached.isEmpty()) {
//                log.info("Redis Hit for Page 0 - Conv {}", conversationId);
//                return cached.stream()
//                        .map(this::deserialize)
//                        .toList();
//            }
//        }

        // 3. Older Pages or Redis Miss = The "Cold" Storage (Postgres)
        log.info("Postgres Read for Page {} - Conv {}", page, conversationId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("sentAt").descending());

        return messageRepository.findByConversationIdOptimized(conversationId, pageable)
                .getContent()
                .stream()
                .map(ChatMessageResponse::fromEntity)
                .toList();
    }

    public Message saveMessage(Message message) {
        return messageRepository.save(message);
    }

    private ChatMessageResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, ChatMessageResponse.class);
        } catch (Exception e) {
            log.error("Failed to deserialize message from Redis", e);
            return null; // The .filter(Objects::nonNull) will clean this up
        }
    }
}
