package com.example.WebChat.DTO;


public record MessageConfirmation(
        String tempId,   // The "uuid-123" from the frontend
        Long realId,     // The "502" from Postgres
        String status    // Usually "PERSISTED" or "SUCCESS"
) {}