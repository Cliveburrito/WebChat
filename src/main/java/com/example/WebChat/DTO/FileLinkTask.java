package com.example.WebChat.DTO;

import java.util.List;

public record FileLinkTask(
        Long messageId,              // The real DB ID we got from the confirmation
        Long conversationId,         // To know which topic to broadcast the result to
        List<String> storageNames,   // The unique names on your Fedora disk (e.g., uuid_img.png)
        List<String> originalNames   // The names the user saw (e.g., "vacation.png")
) {}