package com.example.WebChat.DTO;

import java.time.Instant;

public record ChatMessageResponse(
        String content,
        Instant createdAt,
        String senderUsername,
        Long conversationId
) {}

