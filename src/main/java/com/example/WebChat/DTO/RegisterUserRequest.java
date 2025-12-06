package com.example.WebChat.DTO;

public record RegisterUserRequest(
        String username,
        String email,
        String password
) {
}
