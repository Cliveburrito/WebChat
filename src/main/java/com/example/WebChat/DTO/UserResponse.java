package com.example.WebChat.DTO;

/**
 * Request = what client sends to server
 * Response = what server returns (no password, no hash)
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        String avatarUrl
) {}
