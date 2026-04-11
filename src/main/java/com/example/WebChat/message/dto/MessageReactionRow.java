package com.example.WebChat.message.dto;

public record MessageReactionRow(
        Long messageId,
        String emoji,
        long count,
        boolean reactedByMe
) {}
