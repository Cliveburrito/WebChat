package com.example.WebChat.message.dto;

import java.time.Instant;

public record MessageLifecycleEvent(
        String type,
        Long messageId,
        Long conversationId,
        String content,
        Instant editedAt,
        Instant deletedAt
) {
    public static final String MESSAGE_EDITED = "MESSAGE_EDITED";
    public static final String MESSAGE_DELETED = "MESSAGE_DELETED";
}
