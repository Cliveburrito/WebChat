package com.example.WebChat.auth.dto;

import com.example.WebChat.user.dto.UserResponse;

public record JwtAuthenticationResponse(
        String token,
        UserResponse user
) {}
