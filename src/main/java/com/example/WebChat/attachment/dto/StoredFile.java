package com.example.WebChat.attachment.dto;

public record StoredFile(
        String storageName,
        String contentType,
        long fileSize,
        String thumbnailStorageName
) {}
