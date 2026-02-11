package com.example.WebChat.DTO;

import java.time.Instant;

public interface ChatListRow {
    Long getConversationId();
    String getDisplayName();
    String getLastContent();
    Instant getLastMessageAt();
    Integer getUnreadCount();
    Boolean getMuted();
}
