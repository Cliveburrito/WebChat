package com.example.WebChat.message.dto;

/**
 * Sent by the client to acknowledge receipt or reading of messages.
 */
public record MessageAckRequest(
        Long conversationId,
        Long messageId,
        String type
) {}