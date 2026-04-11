package com.example.WebChat.conversation.dto;


/**
 * The DTO for the request to open a direct chat
 * we just need the ID's of the one that requests it
 * and the person's that he requests it for
 */
public record DirectChatRequest(
        Long id1,
        Long id2
) {
}
