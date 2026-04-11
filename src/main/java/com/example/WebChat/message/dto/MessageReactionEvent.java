package com.example.WebChat.message.dto;

public record MessageReactionEvent(
        Long messageId,
        Long conversationId,
        Long userId,
        String emoji,
        long count,
        String action
) {}
