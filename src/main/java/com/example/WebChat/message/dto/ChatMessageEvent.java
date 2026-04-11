package com.example.WebChat.message.dto;

import java.time.Instant;

public record ChatMessageEvent(
        String tempId,       // From React, to track the "sending..." state
        String content,
        Long userId,
        Long conversationId,
        Instant sentAt,
        Long replyToMessageId,
        String replyToSenderUsername,
        String replyToContent
) {}
