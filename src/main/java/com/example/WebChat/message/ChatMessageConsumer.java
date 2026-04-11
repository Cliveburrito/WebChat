package com.example.WebChat.message;

import com.example.WebChat.attachment.Attachment;
import com.example.WebChat.attachment.AttachmentRepository;
import com.example.WebChat.attachment.dto.AttachmentDTO;
import com.example.WebChat.attachment.dto.AttachmentLinkedEvent;
import com.example.WebChat.attachment.dto.FileLinkTask;
import com.example.WebChat.config.RabbitMQConfig;
import com.example.WebChat.conversation.ConvMembershipRepository;
import com.example.WebChat.conversation.ConversationCacheService;
import com.example.WebChat.conversation.ConversationMembersCacheService;
import com.example.WebChat.conversation.ConversationRepository;
import com.example.WebChat.message.dto.ChatMessageEvent;
import com.example.WebChat.message.dto.ChatMessageResponse;
import com.example.WebChat.message.dto.MessageConfirmation;
import com.example.WebChat.message.dto.WatermarkUpdateEvent;
import com.example.WebChat.user.User;
import com.example.WebChat.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
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
        private final MessageCacheService messageCacheService;
        private final ConversationCacheService conversationCacheService;
        private final ConversationMembersCacheService conversationMembersCacheService;


        @RabbitListener(queues = RabbitMQConfig.CHAT_QUEUE)
        @Transactional
        public void handleMessageProcessing(ChatMessageEvent event) {
            log.info("Processing message: tempId={}", event.tempId());

            User sender = userRepository.getReferenceById(event.userId());

            Message msg = Message.builder()
                    .message(event.content())
                    .sender(sender)
                    .conversation(conversationRepository.getReferenceById(event.conversationId()))
                    .sentAt(event.sentAt())
                    .build();

            if (event.replyToMessageId() != null) {
                Message replyTo = messageRepository.findByIdWithConversation(event.replyToMessageId())
                        .filter(message -> message.getConversation().getId().equals(event.conversationId()))
                        .orElseThrow(() -> new IllegalArgumentException("Reply target not found in conversation: " + event.replyToMessageId()));
                msg.setReplyToMessage(replyTo);
            }

            messageRepository.save(msg);

            // WebSocket confirmation (Transactional safe)
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ChatMessageResponse cachePayload = new ChatMessageResponse(
                            msg.getId(),
                            msg.getMessage() != null ? msg.getMessage() : "",
                            msg.getSentAt(),
                            sender.getUsername(),
                            event.conversationId(),
                            msg.getReplyToMessage() == null ? null : msg.getReplyToMessage().getId(),
                            msg.getReplyToMessage() == null ? null : msg.getReplyToMessage().getSender().getUsername(),
                            msg.getReplyToMessage() == null ? null : msg.getReplyToMessage().isDeleted() ? "Message deleted" : msg.getReplyToMessage().getMessage(),
                            msg.getEditedAt(),
                            msg.getDeletedAt(),
                            msg.isDeleted(),
                            List.of(),
                            List.of()
                    );
                    messageCacheService.updateMessageInCache(event.conversationId(), cachePayload);
                    conversationCacheService.evictUserChats(conversationMembersCacheService.getMemberIds(event.conversationId()));

                    // BROADCAST PERSISTENCE ACK
                    MessageConfirmation confirmation = new MessageConfirmation(event.tempId(), msg.getId(), "PERSISTED");
                    messagingTemplate.convertAndSend("/topic/chat/" + event.conversationId(), confirmation);
                }
            });
        }

        @RabbitListener(queues = RabbitMQConfig.FILE_LINK_QUEUE)
        @Transactional
        public void handleFileLinking(FileLinkTask task) {
            log.info("Surgically linking files to msg {}", task.messageId());
            try {
                Message message = messageRepository.findByIdWithSenderAndConversation(task.messageId())
                        .orElseThrow(() -> new IllegalArgumentException("Message not found: " + task.messageId()));
                Long actualConversationId = message.getConversation().getId();
                Long actualSenderId = message.getSender().getId();

                if (!actualConversationId.equals(task.conversationId())) {
                    throw new IllegalArgumentException("Message does not belong to the requested conversation.");
                }

                if (!actualSenderId.equals(task.uploaderId())) {
                    throw new AccessDeniedException("Only the message sender can attach files to this message.");
                }

                List<Attachment> attachments = new java.util.ArrayList<>();
                List<AttachmentDTO> attachmentDtos = new java.util.ArrayList<>();
                for (int i = 0; i < task.storageNames().size(); i++) {
                    String thumbnailStorageName = task.thumbnailStorageNames().get(i);
                    attachments.add(Attachment.builder()
                            .storageName(task.storageNames().get(i))
                            .originalName(task.originalNames().get(i))
                            .contentType(task.contentTypes().get(i))
                            .fileSize(task.fileSizes().get(i))
                            .thumbnailUrl(thumbnailStorageName)
                            .message(message)
                            .conversation(message.getConversation())
                            .uploadedBy(message.getSender())
                            .build());

                    String category = "FILE";
                    String contentType = task.contentTypes().get(i);
                    if (contentType != null) {
                        String mime = contentType.toLowerCase();
                        if (mime.startsWith("image/")) category = "IMAGE";
                        else if (mime.startsWith("video/")) category = "VIDEO";
                        else if (mime.startsWith("audio/")) category = "AUDIO";
                    }

                    attachmentDtos.add(new AttachmentDTO(
                            null,
                            task.storageNames().get(i),
                            task.originalNames().get(i),
                            contentType,
                            category,
                            task.fileSizes().get(i),
                            thumbnailStorageName,
                            message.getSender().getUsername(),
                            message.getId(),
                            task.conversationId(),
                            message.getSentAt()
                    ));
                }
                attachmentRepository.saveAll(attachments);

                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        ChatMessageResponse cachePayload = new ChatMessageResponse(
                                message.getId(),
                                message.getMessage() != null ? message.getMessage() : "",
                                message.getSentAt(),
                                message.getSender().getUsername(),
                                task.conversationId(),
                                message.getReplyToMessage() == null ? null : message.getReplyToMessage().getId(),
                                message.getReplyToMessage() == null ? null : message.getReplyToMessage().getSender().getUsername(),
                                message.getReplyToMessage() == null ? null : message.getReplyToMessage().isDeleted() ? "Message deleted" : message.getReplyToMessage().getMessage(),
                                message.getEditedAt(),
                                message.getDeletedAt(),
                                message.isDeleted(),
                                List.of(),
                                attachmentDtos
                        );
                        messageCacheService.updateMessageInCache(task.conversationId(), cachePayload);
                        conversationCacheService.evictUserChats(conversationMembersCacheService.getMemberIds(task.conversationId()));

                        AttachmentLinkedEvent linkedEvent = new AttachmentLinkedEvent(task.messageId(), task.conversationId(), attachmentDtos);
                        messagingTemplate.convertAndSend("/topic/chat/" + task.conversationId(), linkedEvent);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to link files", e);
                throw e;
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

            int updatedRows;
            if ("READ".equals(type)) {
                updatedRows = convMembershipRepository.updateLastReadId(userId, conversationId, messageId);
            } else {
                updatedRows = convMembershipRepository.updateLastDeliveredId(userId, conversationId, messageId);
            }

            if (updatedRows == 0) {
                log.debug("Skipped stale {} watermark for user {} in chat {} at message {}", type, userId, conversationId, messageId);
                return;
            }

            conversationCacheService.evictUserChats(userId);
        }
    }
