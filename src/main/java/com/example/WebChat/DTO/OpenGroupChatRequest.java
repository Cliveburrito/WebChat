package com.example.WebChat.DTO;

import java.util.List;


public record OpenGroupChatRequest(
        List<Long> memberIds,
        String groupName
){}
