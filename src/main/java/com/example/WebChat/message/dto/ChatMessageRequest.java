package com.example.WebChat.message.dto;

/**
 * This is just a 'box' to catch the incoming WebSocket JSON.
 * React sends: { "content": "Hello", "tempId": "uuid-123" }
 */
public record ChatMessageRequest(
        String content,
        String tempId,
        Long replyToMessageId
) {}
