package com.example.WebChat.attachment.dto;

import java.util.List;

public record AttachmentLinkedEvent(
        Long messageId,
        Long conversationId,
        List<AttachmentDTO> attachments
) {}
