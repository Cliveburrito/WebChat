package com.example.WebChat.conversation.dto;

public record ConversationWatermarkResponse(
        Long userId,
        Long lastDeliveredMessageId,
        Long lastReadMessageId
) {}
