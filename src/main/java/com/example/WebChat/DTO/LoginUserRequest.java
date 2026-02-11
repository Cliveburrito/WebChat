package com.example.WebChat.DTO;


/**
 * The DTO for a Login request, the user only types his username and password
 * so that's what we need
 */
public record LoginUserRequest(
        String username,
        String password
) {
}
