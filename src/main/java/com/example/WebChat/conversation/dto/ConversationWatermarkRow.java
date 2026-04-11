package com.example.WebChat.conversation.dto;

public interface ConversationWatermarkRow {
    Long getConversationId();
    Long getUserId();
    Long getLastDeliveredMessageId();
    Long getLastReadMessageId();
}
