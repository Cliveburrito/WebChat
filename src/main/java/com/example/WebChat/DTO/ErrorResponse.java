package com.example.WebChat.DTO;


/**
 * Object for the error response sent to the frontend whenever...
 * well whenever we get an http error
 */
public record ErrorResponse(
        int status,
        String message,
        long timestamp
) {
    public ErrorResponse(int status, String message) {
        this(status, message, System.currentTimeMillis());
    }
}
