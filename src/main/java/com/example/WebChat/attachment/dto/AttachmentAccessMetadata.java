package com.example.WebChat.attachment.dto;

public record AttachmentAccessMetadata(
        Long conversationId,
        String storageName,
        String originalName,
        String contentType,
        String thumbnailStorageName
) {}
