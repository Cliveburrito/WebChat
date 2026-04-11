package com.example.WebChat.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The DTO for a Login request, the user only types his username and password
 * so that's what we need
 */
public record LoginUserRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {}