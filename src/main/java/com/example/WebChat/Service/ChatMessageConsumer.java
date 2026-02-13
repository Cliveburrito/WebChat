package com.example.WebChat.Service;

import com.example.WebChat.DTO.*;
import com.example.WebChat.Entity.Attachment;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Entity.Message;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Exception.ResourceNotFoundException;
import com.example.WebChat.Repository.*;
import com.example.WebChat.UtilsConfigs.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatMessageConsumer {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConvMembershipRepository convMembershipRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AttachmentRepository attachmentRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.CHAT_QUEUE)
    @Transactional
    public void handleMessageProcessing(ChatMessageEvent event) { // Changed to Event
        log.info("Processing message from RabbitMQ: tempId={}", event.tempId());

        try {
            User sender = userRepository.findByUsername(event.senderName())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            // Use getReferenceById to avoid a heavy SELECT query
            Conversation conv = conversationRepository.getReferenceById(event.conversationId());

            Message msg = Message.builder()
                    .message(event.content())
                    .sender(sender)
                    .conversation(conv)
                    .sentAt(event.sentAt())
                    .build();

            // This generates the real database ID
            messageRepository.save(msg);

            // --- REDIS: Update Chat History (Sliding Window) ---
            String historyKey = "chat:history:" + event.conversationId();
            ChatMessageResponse response = ChatMessageResponse.fromEntity(msg);
            String msgJson = objectMapper.writeValueAsString(response);

            // Push newest to the left, trim to keep only 50
            redisTemplate.opsForList().leftPush(historyKey, msgJson);
            redisTemplate.opsForList().trim(historyKey, 0, 49);
            redisTemplate.expire(historyKey, Duration.ofDays(7));

            // --- REDIS: Update Sidebar Metadata (Hash) ---
            // This makes the 'getUserChats' incredibly fast later
            String metaKey = "conv:meta:" + event.conversationId();
            redisTemplate.opsForHash().put(metaKey, "lastContent", event.content());
            redisTemplate.opsForHash().put(metaKey, "lastMessageAt", event.sentAt().toString());

            // Notify the frontend: "tempId abc is now real ID 123"
            // This is crucial for the asynchronous file linking later!
            MessageConfirmation confirmation = new MessageConfirmation(
                    event.tempId(),
                    msg.getId(),
                    "PERSISTED"
            );
            messagingTemplate.convertAndSend("/topic/chat/" + event.conversationId(), confirmation);

            // Unread count and notifications
            convMembershipRepository.incrementUnreadCountForOthers(event.conversationId(), sender.getId());

            log.info("Message saved with ID: {}", msg.getId());

        } catch (Exception e) {
            log.error("Failed to process message from RabbitMQ", e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.FILE_LINK_QUEUE)
    @Transactional
    public void handleFileLinking(FileLinkTask task) {
        log.info("Linking {} files to message ID {}", task.storageNames().size(), task.messageId());

        try {
            // Get a reference to the message just saved above
            Message message = messageRepository.getReferenceById(task.messageId());

            //  Create Attachment entities for each file on my disk
            List<Attachment> attachments = new java.util.ArrayList<>();
            for (int i = 0; i < task.storageNames().size(); i++) {
                attachments.add(Attachment.builder()
                        .storageName(task.storageNames().get(i))
                        .originalName(task.originalNames().get(i))
                        .message(message)
                        .conversation(message.getConversation())
                        .uploadedBy(message.getSender())
                        .build());
            }
            attachmentRepository.saveAll(attachments);

            // Broadcast the event to the chat
            List<AttachmentDTO> dtos = attachments.stream().map(AttachmentDTO::fromEntity).toList();
            AttachmentLinkedEvent linkedEvent = new AttachmentLinkedEvent(task.messageId(), task.conversationId(), dtos);

            messagingTemplate.convertAndSend("/topic/chat/" + task.conversationId(), linkedEvent);

        } catch (Exception e) {
            log.error("Failed to link files in background", e);
        }
    }
}