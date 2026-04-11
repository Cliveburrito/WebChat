package com.example.WebChat.message.dto;

public record MessageReactionSummary(
        String emoji,
        long count,
        boolean reactedByMe
) {}
