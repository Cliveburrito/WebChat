package com.example.WebChat.DTO;



import com.example.WebChat.Entity.Message;

import java.time.Instant;
import java.util.List;

/**
 * The object used for the messages shown in the chat
 */

public record ChatMessageResponse(
        String content,
        Instant createdAt,
        String senderUsername,
        Long conversationId,
        List<AttachmentDTO> attachments
) {

    public static ChatMessageResponse fromEntity(Message message) {
        return new ChatMessageResponse(
                // Βεβαιώσου ότι το όνομα του πεδίου είναι "content" για να ταιριάζει με το JSON σου
                message.getMessage() != null ? message.getMessage() : "",
                message.getSentAt(),
                message.getSender().getUsername(),
                message.getConversation().getConversationID(),
                // ✅ ΕΔΩ ΓΙΝΕΤΑΙ Η ΜΕΤΑΤΡΟΠΗ
                message.getAttachments() == null ? List.of() : message.getAttachments().stream()
                        .map(AttachmentDTO::fromEntity)
                        .toList()
        );
    }
}

