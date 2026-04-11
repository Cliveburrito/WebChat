package com.example.WebChat.conversation.dto;


import java.time.Instant;

/**
 * The object used for the conversation preview on the sidebar of the app
 */
public record ConversationResponse(
        Long conversationId,
        String displayName,
        String avatarUrl,
        String lastContent,
        Long unreadCount,
        Instant lastMessageAt,
        Long lastMessageId,
        Long lastSenderId,
        Boolean isGroup,
        Boolean muted,
        Instant directParticipantLastSeenAt,
        Long myLastDeliveredMessageId,
        Long myLastReadMessageId,
        java.util.List<ConversationWatermarkResponse> participantWatermarks
) {}
