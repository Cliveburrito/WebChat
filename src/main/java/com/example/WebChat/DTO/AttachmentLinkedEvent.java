package com.example.WebChat.DTO;

import java.util.List;

public record AttachmentLinkedEvent(

        Long messageId,
        Long conversationId,
        List<AttachmentDTO> attachments // The metadata (UUIDs and Names)
) {}
