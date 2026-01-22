package com.example.WebChat.Service;

import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Entity.ConvMembership;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Entity.Message;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Repository.ConvMembershipRepository;
import com.example.WebChat.Repository.ConversationRepository;
import com.example.WebChat.Repository.MessageRepository;
import com.example.WebChat.Repository.UserRepository;
import io.github.bucket4j.Bucket;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.util.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;


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
        message = messageRepository.save(message);

        return message;
    }
    /**
     * Processes a message sent via WebSocket, saves it, and broadcasts it to the subscribers.
     */
    @Transactional
    public ChatMessageResponse processAndSend(String username, Long conversationId, String content) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        boolean isMember = convMembershipRepository.existsByUser_IdAndConversation_ConversationID(user.getId(), conversationId);
        if (!isMember) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this conversation.");

        Bucket bucket = rateLimiter.resolveMessageBucket(username);
        if(!bucket.tryConsume(1)) {
            log.warn("User {} is spamming messages...",  username);
            throw new RuntimeException("Too many messages!");
        }

        Message msg = Message.builder()
                .message(content)
                .sender(user)
                .conversation(conversation)
                .sentAt(Instant.now())
                .build();

        msg = messageRepository.save(msg);

        ChatMessageResponse dto = new ChatMessageResponse(
                msg.getMessage(),
                msg.getSentAt(),
                user.getUsername(),
                conversationId
        );

        // στείλε στη συνομιλία
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, dto);

        // unread + notifications
        List<ConvMembership> members = convMembershipRepository.findAllByConversation_ConversationID(conversationId);
        for (ConvMembership m : members) {
            if (!m.getUser().getUsername().equals(username)) {
                m.setUnreadCount(m.getUnreadCount() + 1);
                convMembershipRepository.save(m);
            }
            messagingTemplate.convertAndSend("/topic/notifications/" + m.getUser().getUsername(), dto);
        }
        log.info("Broadcasting message from {} to conversation {}", username, conversationId);

        return dto;
    }


    public void postMessage(String username, Long conversationId, String content) {
        Optional<User> user = userRepository.findByUsername(username);
        Conversation conversation = conversationRepository.findByConversationID(conversationId);

        Message message = Message.builder()
                .conversation(conversation)
                .sender(user.get())
                .sentAt(Instant.now())
                .message(content)
                .build();

        message = messageRepository.save(message);

        ChatMessageResponse dto = new ChatMessageResponse(
                message.getMessage(),
                message.getSentAt(),
                message.getSender().getUsername(),
                message.getConversation().getConversationID());

    }

    /**
     * Retrieves the history of messages for a conversation.
     * Note: For security, you should also verify membership here if calling from a generic controller.
     */
    public List<ChatMessageResponse> getChatHistory(Long conversationId, String requestingUser) {
        // Security Check
        boolean isMember = convMembershipRepository.existsByUser_UsernameAndConversation_ConversationID(
                requestingUser, conversationId);

        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to chat history.");
        }

        List<Message> messages = messageRepository.findAllByConversation_ConversationIDOrderBySentAtAsc(conversationId);

        return messages.stream()
                .map(m -> new ChatMessageResponse(
                        m.getMessage(),
                        m.getSentAt(),
                        (m.getSender() != null) ? m.getSender().getUsername() : "Deleted User",
                        conversationId
                ))
                .toList();
    }
}
