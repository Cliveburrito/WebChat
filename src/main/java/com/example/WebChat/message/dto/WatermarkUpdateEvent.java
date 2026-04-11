package com.example.WebChat.message.dto;

/**
 * Broadcast to all participants to update the visual "ticks" in the UI.
 */
public record WatermarkUpdateEvent(
        Long conversationId,
        Long userId,      // Who performed the action
        Long messageId,   // The watermark point
        String type    // "DELIVERED" or "READ"
) {}