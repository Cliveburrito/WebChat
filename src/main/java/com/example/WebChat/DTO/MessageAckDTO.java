package com.example.WebChat.DTO;

/**
 * Sent by the client to acknowledge receipt or reading of messages.
 */
public record MessageAckDTO(
        Long conversationId,
        Long messageId,
        // "DELIVERED" (received by app) or "READ" (opened chat)
        String type
) {}