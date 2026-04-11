package com.example.WebChat.user.dto;

import com.example.WebChat.user.User;

import java.io.Serializable;

/**
 * Request = what client sends to server
 * Response = what server returns (no password, no hash)
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        String avatarUrl
) implements Serializable {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl());
    }
}
