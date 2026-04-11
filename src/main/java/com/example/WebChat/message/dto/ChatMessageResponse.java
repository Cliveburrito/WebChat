package com.example.WebChat.message.dto;



import com.example.WebChat.attachment.dto.AttachmentDTO;
import com.example.WebChat.message.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The object used for the messages shown in the chat
 */

public record ChatMessageResponse(
        Long id,
        String content,
        Instant createdAt,
        String senderUsername,
        Long conversationId,
        Long replyToMessageId,
        String replyToSenderUsername,
        String replyToContent,
        List<MessageReactionSummary> reactions,
        List<AttachmentDTO> attachments
) {

    public static ChatMessageResponse fromEntity(Message message) {
        // Force the list to be a standard ArrayList
        List<AttachmentDTO> attachmentList = (message.getAttachments() == null)
                ? new ArrayList<>()
                : message.getAttachments().stream()
                .map(AttachmentDTO::fromEntity)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        return new ChatMessageResponse(
                message.getId(),
                message.getMessage() != null ? message.getMessage() : "",
                message.getSentAt(),
                message.getSender().getUsername(),
                message.getConversation().getId(),
                message.getReplyToMessage() == null ? null : message.getReplyToMessage().getId(),
                message.getReplyToMessage() == null ? null : message.getReplyToMessage().getSender().getUsername(),
                message.getReplyToMessage() == null ? null : message.getReplyToMessage().getMessage(),
                List.of(),
                attachmentList
        );
    }
}

