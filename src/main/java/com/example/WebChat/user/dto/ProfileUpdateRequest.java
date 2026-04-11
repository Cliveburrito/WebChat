package com.example.WebChat.user.dto;

import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Size(max = 80)
        String displayName,

        @Size(max = 280)
        String bio
) {}
