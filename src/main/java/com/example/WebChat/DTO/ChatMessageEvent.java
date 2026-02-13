package com.example.WebChat.DTO;

import java.time.Instant;

public record ChatMessageEvent(
        String tempId,       // From React, to track the "sending..." state
        String content,
        String senderName,
        Long conversationId,
        Instant sentAt
) {}