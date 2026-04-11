package com.example.WebChat.attachment.dto;

public interface AttachmentAccessRow {
    Long getConversationId();
    String getStorageName();
    String getOriginalName();
    String getContentType();
    String getThumbnailUrl();
}
