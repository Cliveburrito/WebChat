package com.example.WebChat.user.dto;

import com.example.WebChat.user.User;

import java.io.Serializable;
import java.time.Instant;

/**
 * Request = what client sends to server
 * Response = what server returns (no password, no hash)
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        String displayName,
        String bio,
        String avatarUrl,
        Instant lastSeenAt
) implements Serializable {
    public UserResponse(Long id, String username, String email, String avatarUrl) {
        this(id, username, email, null, null, avatarUrl, null);
    }

    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                user.getAvatarUrl(),
                user.getLastSeenAt());
    }
}
