package com.example.WebChat.Service;

import com.example.WebChat.DTO.*;
import com.example.WebChat.Entity.Attachment;
import com.example.WebChat.Entity.Message;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Exception.ResourceNotFoundException;
import com.example.WebChat.Repository.*;
import com.example.WebChat.UtilsConfigs.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


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
    private final MessageService messageService; // Inject for caching logic


    @RabbitListener(queues = RabbitMQConfig.CHAT_QUEUE)
    @Transactional
    public void handleMessageProcessing(ChatMessageEvent event) {
        log.info("Processing message: tempId={}", event.tempId());

        User sender = userRepository.findByUsername(event.senderName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Message msg = Message.builder()
                .message(event.content())
                .sender(sender)
                .conversation(conversationRepository.getReferenceById(event.conversationId()))
                .sentAt(event.sentAt())
                .build();

        messageRepository.save(msg);

        // Surgical Cache Update (ZSet + Hash)
        messageService.updateMessageInCache(event.conversationId(), ChatMessageResponse.fromEntity(msg));

        // WebSocket confirmation (Transactional safe)
        MessageConfirmation confirmation = new MessageConfirmation(event.tempId(), msg.getId(), "PERSISTED");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messagingTemplate.convertAndSend("/topic/chat/" + event.conversationId(), confirmation);
            }
        });
    }

    @RabbitListener(queues = RabbitMQConfig.FILE_LINK_QUEUE)
    @Transactional
    public void handleFileLinking(FileLinkTask task) {
        log.info("Surgically linking files to msg {}", task.messageId());
        try {
            Message message = messageRepository.getReferenceById(task.messageId());

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

            // 🚀 THE BEAST FIX: Update only the specific message JSON in the Hash
            // No need to delete the whole history anymore!
            messageService.updateMessageInCache(task.conversationId(), ChatMessageResponse.fromEntity(message));

            // 3. Live WebSocket update
            List<AttachmentDTO> dtos = attachments.stream().map(AttachmentDTO::fromEntity).toList();
            AttachmentLinkedEvent linkedEvent = new AttachmentLinkedEvent(task.messageId(), task.conversationId(), dtos);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend("/topic/chat/" + task.conversationId(), linkedEvent);
                }
            });
        } catch (Exception e) {
            log.error("Failed to link files", e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.WATERMARK_QUEUE)
    @Transactional
    public void handleWatermarkPersistence(WatermarkUpdateEvent wue) {
        Long userId = wue.userId();
        Long conversationId = wue.conversationId();
        Long messageId = wue.messageId();
        String type = wue.type();
        log.info("Persisting {} watermark for user {} to DB", wue.type(), wue.userId() );

        if ("READ".equals(type)) {
            convMembershipRepository.updateLastReadId(userId, conversationId, messageId);
        } else {
            convMembershipRepository.updateLastDeliveredId(userId, conversationId, messageId);
        }
    }
}