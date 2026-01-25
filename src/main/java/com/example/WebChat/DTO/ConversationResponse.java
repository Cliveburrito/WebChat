package com.example.WebChat.DTO;


import java.time.Instant;

/**
 * The object used for the conversation preview on the sidebar of the app
 */
public record ConversationResponse(
        Long id,
        String name,
        String avatarUrl,
        String lastMessage,
        int unreadCount,
        Instant lastMessageAt
) {}