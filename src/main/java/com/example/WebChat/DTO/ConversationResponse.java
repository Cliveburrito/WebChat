package com.example.WebChat.DTO;


import java.time.Instant;

/**
 * The object used for the conversation preview on the sidebar of the app
 */
public record ConversationResponse(
        Long conversationId,
        String displayName,
        String avatarUrl, // (ή displayImage)
        String lastContent,
        Long unreadCount,
        Instant lastMessageAt,
        Long lastMessageId,
        Long lastSenderId,
        Boolean isGroup,    // Χρήσιμο για το Frontend
        Boolean muted
) {}