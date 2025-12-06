package com.example.WebChat.Service;

import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Entity.Message;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Repository.ConversationRepository;
import com.example.WebChat.Repository.MessageRepository;
import com.example.WebChat.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

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

    private final SimpMessagingTemplate messagingTemplate;
    // + ό,τι repositories έχεις

    public void processAndSend(String username, Long conversationId, String content) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

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
                user.getUsername(),          // 👈 senderUsername
                conversation.getConversationID()
        );

        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, dto);
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

    public List<ChatMessageResponse> getChatHistory(Long conversationId) {
        // 1. Explicitly check if the conversation exists first
        if (!conversationRepository.existsById(conversationId)) {
            throw new RuntimeException("Conversation with ID " + conversationId + " does not exist.");
        }

        try {
            // 2. Fetch messages with the JOIN FETCH to prevent N+1
            List<Message> messages = messageRepository.findByConversation_ConversationID(conversationId);

            if (messages.isEmpty()) {
                // Optional: Return empty list instead of error if just no messages yet
                return List.of();
            }

            // 3. Map safely with null checks
            return messages.stream()
                    .map(m -> new ChatMessageResponse(
                            m.getMessage(),
                            m.getSentAt(),
                            (m.getSender() != null) ? m.getSender().getUsername() : "Unknown User", // Safety check
                            conversationId
                    ))
                    .toList();

        } catch (Exception e) {
            // 4. Capture unexpected DB errors
            throw new RuntimeException("Database error while fetching messages: " + e.getMessage());
        }
    }
}
