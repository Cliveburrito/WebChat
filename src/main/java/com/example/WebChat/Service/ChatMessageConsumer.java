package com.example.WebChat.Service;

import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Entity.Message;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Exception.ResourceNotFoundException;
import com.example.WebChat.Repository.ConvMembershipRepository;
import com.example.WebChat.Repository.ConversationRepository;
import com.example.WebChat.Repository.MessageRepository;
import com.example.WebChat.Repository.UserRepository;
import com.example.WebChat.UtilsConfigs.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    @RabbitListener(queues = RabbitMQConfig.CHAT_QUEUE)
    @Transactional
    public void handleMessageProcessing(ChatMessageResponse dto) {
        log.info("Processing message from RabbitMQ: {}", dto.content());

        try {
            // save the msg in the db
            User sender = userRepository.findByUsername(dto.senderUsername())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            Conversation conv = conversationRepository.getReferenceById(dto.conversationId());

            Message msg = Message.builder()
                    .message(dto.content())
                    .sender(sender)
                    .conversation(conv)
                    .sentAt(dto.createdAt())
                    .build();
            messageRepository.save(msg);

            // write to the db the unread counts
            convMembershipRepository.incrementUnreadCountForOthers(dto.conversationId(), sender.getId());

            // async notifications
            List<String> memberUsernames = convMembershipRepository.findUsernamesByConversationId(dto.conversationId());
            for (String recipient : memberUsernames) {
                // not to ourselves
                if (!recipient.equals(dto.senderUsername())) {
                    messagingTemplate.convertAndSend("/topic/notifications/" + recipient, dto);

                }
            }

            log.info("Async processing finished for message in conv {}", dto.conversationId());

        } catch (Exception e) {
            log.error("Failed to process message from RabbitMQ", e);
        }
    }
}