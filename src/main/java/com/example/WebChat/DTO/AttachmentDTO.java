package com.example.WebChat.DTO;

import com.example.WebChat.Entity.Attachment;

/**
 * Data Transfer Object for file attachments.
 * Used for sending file metadata to the React frontend.
 */
public record AttachmentDTO(
        Integer id,
        String storageName,
        String originalName,
        String contentType,
        Long fileSize,
        String thumbnailUrl,
        String uploadedBy,
        Long messageId,
        Long conversationId
) {
    // A static factory method is a clean way to convert the Entity to a Record
    public static AttachmentDTO fromEntity(Attachment attachment) {
        return new AttachmentDTO(
                attachment.getId(),
                attachment.getStorageName(),
                attachment.getOriginalName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getThumbnailUrl(),
                attachment.getUploadedBy() != null ? attachment.getUploadedBy().getUsername() : null,
                attachment.getMessage() != null ? attachment.getMessage().getId() : null,
                attachment.getConversation() != null ? attachment.getConversation().getConversationID() : null
        );
    }
}
