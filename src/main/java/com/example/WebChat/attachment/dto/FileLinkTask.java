package com.example.WebChat.attachment.dto;

import java.util.List;

public record FileLinkTask(
        Long messageId,
        Long conversationId,
        Long uploaderId,
        List<String> storageNames,
        List<String> originalNames,
        List<String> contentTypes,
        List<Long> fileSizes,
        List<String> thumbnailStorageNames
) {}
