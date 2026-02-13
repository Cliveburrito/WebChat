package com.example.WebChat.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The DTO for the request to open a group chat
 * we just need the ID's of the one that requests it
 * and the persons ids that he requests it for, also a group chat must have a name
 */
public record OpenGroupChatRequest(
        @NotBlank(message = "Group name is required")
        @Size(min = 1, max = 50)
        String groupName,

        @NotNull
        @Size(min = 2, message = "You must add at least one other member")
        List<Long> memberIds
) {}