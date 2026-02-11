package com.example.WebChat.DTO;



import java.time.Instant;

/**
 * The object used for the messages shown in the chat
 */

public record ChatMessageResponse(
        String content,
        Instant createdAt,
        String senderUsername,
        Long conversationId
) {

    @Override
    public String content() {
        return content;
    }

    @Override
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public String senderUsername() {
        return senderUsername;
    }

    @Override
    public Long conversationId() {
        return conversationId;
    }
}

