package com.example.WebChat.Service;

import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Entity.Message;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Exception.RateLimitExceededException;
import com.example.WebChat.Exception.ResourceNotFoundException;
import com.example.WebChat.Repository.ConvMembershipRepository;
import com.example.WebChat.Repository.ConversationRepository;
import com.example.WebChat.Repository.MessageRepository;
import com.example.WebChat.Repository.UserRepository;
import io.github.bucket4j.Bucket;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final ConversationRepository conversationRepository;
    private final ConvMembershipRepository convMembershipRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RateLimiterService rateLimiter;

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
    public ChatMessageResponse processAndSend(String username, Long conversationId, String content) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        Conversation conversation = conversationRepository.getReferenceById(conversationId);

        boolean isMember = convMembershipRepository.existsByUser_IdAndConversation_ConversationID(user.getId(), conversationId);
        if (!isMember) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this conversation.");

        Bucket bucket = rateLimiter.resolveMessageBucket(username);
        if(!bucket.tryConsume(1)) {
            log.warn("User {} is spamming messages...",  username);
            throw new RateLimitExceededException("Too many messages!");
        }

        Message msg = Message.builder()
                .message(content)
                .sender(user)
                .conversation(conversation)
                .sentAt(Instant.now())
                .build();

        saveMessage(msg);

        ChatMessageResponse dto = new ChatMessageResponse(
                msg.getMessage(),
                msg.getSentAt(),
                user.getUsername(),
                conversationId
        );
        // the frontend doesn't subscribe to this topic, but we are keeping it
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, dto);

        // unread + notifications
        List<String> memberUsernames = convMembershipRepository.findUsernamesByConversationId(conversationId);

        for (String recipient : memberUsernames) {
            messagingTemplate.convertAndSend("/topic/notifications/" + recipient, dto);
        }

        convMembershipRepository.incrementUnreadCountForOthers(conversationId, user.getId());

        log.info("Broadcasting message from {} to conversation {}", username, conversationId);

        return dto;
    }

    /**
     * I used that for Postman , although it's useless now basically I'll keep it
     */
    public void postMessage(String username, Long conversationId, String content) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User Does NOT FOUND" + username));
        Conversation conversation = conversationRepository.findByConversationID(conversationId);

        Message message = Message.builder()
                .conversation(conversation)
                .sender(user)
                .sentAt(Instant.now())
                .message(content)
                .build();

        saveMessage(message);
    }

    /**
     * Retrieves the history of messages for a conversation.
     * Note: For security, you should also verify membership here if calling from a generic controller.
     */
    public Page<ChatMessageResponse> getChatHistory(Long conversationId, Pageable pageable) {
        // Fetch EVERYTHING in one query
        Page<Message> messages = messageRepository.findByConversationIdWithSender(conversationId, pageable);

        // Map to DTO (The sender data is already in memory!)
        return messages.map(m -> new ChatMessageResponse(
                m.getMessage(),
                m.getSentAt(),
                m.getSender().getUsername(),
                conversationId
        ));
    }

    public Message saveMessage(Message message) {
        return messageRepository.save(message);
    }
}
