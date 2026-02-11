package com.example.WebChat.DTO;

import java.util.List;

/**
 * The DTO for the request to open a group chat
 * we just need the ID's of the one that requests it
 * and the persons ids that he requests it for, also a group chat must have a name
 */
public record OpenGroupChatRequest(
        List<Long> memberIds,
        String groupName
){}
