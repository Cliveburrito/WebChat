package com.example.WebChat.attachment.dto;

import com.example.WebChat.attachment.Attachment;

/**
 * Data Transfer Object for file attachments.
 * Used for sending file metadata to the React frontend.
 */
public record AttachmentDTO(
        Integer id,
        String storageName,
        String originalName,
        String contentType,
        String fileCategory, // <--- Το προσθέτουμε εδώ για να πηγαίνει στο JSON
        Long fileSize,
        String thumbnailUrl,
        String uploadedBy,
        Long messageId,
        Long conversationId,
        java.time.Instant sentAt // <--- Κρίσιμο για το sorting στο Gallery
) {
    public static AttachmentDTO fromEntity(Attachment attachment) {
        String category = "FILE";
        if (attachment.getContentType() != null) {
            String mime = attachment.getContentType().toLowerCase();
            if (mime.startsWith("image/")) category = "IMAGE";
            else if (mime.startsWith("video/")) category = "VIDEO";
            else if (mime.startsWith("audio/")) category = "AUDIO";
        }

        return new AttachmentDTO(
                attachment.getId(),
                attachment.getStorageName(),
                attachment.getOriginalName(),
                attachment.getContentType(),
                category,
                attachment.getFileSize(),
                attachment.getThumbnailUrl(),
                attachment.getUploadedBy() != null ? attachment.getUploadedBy().getUsername() : null,
                attachment.getMessage() != null ? attachment.getMessage().getId() : null,
                attachment.getConversation() != null ? attachment.getConversation().getId() : null,
                attachment.getMessage() != null ? attachment.getMessage().getSentAt() : null
        );
    }
}