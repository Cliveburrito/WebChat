package com.example.WebChat.DTO;

/**
 * The DTO for a register request, the user only types his username ,password and email
 * so that's what we need
 */
public record RegisterUserRequest(
        String username,
        String email,
        String password
) {
}
