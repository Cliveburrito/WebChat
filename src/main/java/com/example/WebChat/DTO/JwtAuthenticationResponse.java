package com.example.WebChat.DTO;


public record JwtAuthenticationResponse(
        String token,
        UserResponse user
) {}