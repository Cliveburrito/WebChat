package com.example.WebChat.DTO;

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

    // Ωραίο helper method!
    default String getSidebarSnippet() {
        if (getLastContent() == null || getLastContent().isBlank()) {
            // Μπορείς να επιστρέφεις null ή κενό αν θες να το χειριστεί το React
            // ή ένα placeholder κείμενο
            return "";
        }
        return getLastContent();
    }
}