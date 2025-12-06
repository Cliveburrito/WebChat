package com.example.WebChat.DTO;

/**
 * Request = what client sends to server
 *<p></p>
 * Response = what server returns (no password, no hash)
 * @param id
 * @param username
 * @param email
 * @param avatarUrl
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        String avatarUrl
) {}
