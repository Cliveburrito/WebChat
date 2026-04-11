package com.example.WebChat.message.dto;


public record MessageConfirmation(
        String tempId,
        Long realId,
        String status
) {}