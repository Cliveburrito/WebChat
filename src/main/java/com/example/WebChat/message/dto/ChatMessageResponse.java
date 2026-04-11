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
        Instant editedAt,
        Instant deletedAt,
        boolean deleted,
        List<MessageReactionSummary> reactions,
        List<AttachmentDTO> attachments
) {

    public static ChatMessageResponse fromEntity(Message message) {
        boolean deleted = message.isDeleted();
        // Force the list to be a standard ArrayList
        List<AttachmentDTO> attachmentList = (deleted || message.getAttachments() == null)
                ? new ArrayList<>()
                : message.getAttachments().stream()
                .map(AttachmentDTO::fromEntity)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Message replyToMessage = message.getReplyToMessage();

        return new ChatMessageResponse(
                message.getId(),
                deleted || message.getMessage() == null ? "" : message.getMessage(),
                message.getSentAt(),
                message.getSender().getUsername(),
                message.getConversation().getId(),
                replyToMessage == null ? null : replyToMessage.getId(),
                replyToMessage == null ? null : replyToMessage.getSender().getUsername(),
                replyToMessage == null ? null : replyToMessage.isDeleted() ? "Message deleted" : replyToMessage.getMessage(),
                message.getEditedAt(),
                message.getDeletedAt(),
                deleted,
                List.of(),
                attachmentList
        );
    }
}

