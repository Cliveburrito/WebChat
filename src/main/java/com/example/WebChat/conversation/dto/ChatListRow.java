package com.example.WebChat.conversation.dto;

import java.time.Instant;

public interface ChatListRow {
    Long getConversationId();
    String getDisplayName();
    String getLastContent();
    Instant getLastMessageAt();
    Long getLastMessageId();
    Long getLastSenderId();
    Long getUnreadCount();
    Boolean getIsGroup();
    Boolean getMuted();
    Long getMyLastDeliveredMessageId();
    Long getMyLastReadMessageId();

    default String getSidebarSnippet() {
        if (getLastContent() == null || getLastContent().isBlank()) {
            return "";
        }
        return getLastContent();
    }
}
