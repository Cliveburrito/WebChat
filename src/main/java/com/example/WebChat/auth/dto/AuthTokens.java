package com.example.WebChat.auth.dto;

import com.example.WebChat.user.dto.UserResponse;

public record AuthTokens(
        String accessToken,
        String refreshToken,
        UserResponse user
) {}
