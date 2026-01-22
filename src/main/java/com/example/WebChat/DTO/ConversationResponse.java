package com.example.WebChat.DTO;


import java.time.Instant;

public record ConversationResponse(
        Long id,
        String name,
        String avatarUrl,
        String lastMessage,
        int unreadCount,
        Instant lastMessageAt
) {}