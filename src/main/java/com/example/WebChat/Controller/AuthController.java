package com.example.WebChat.Controller;

import com.example.WebChat.DTO.JwtAuthenticationResponse;
import com.example.WebChat.DTO.LoginUserRequest;
import com.example.WebChat.DTO.RegisterUserRequest;
import com.example.WebChat.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<JwtAuthenticationResponse> register(
            @RequestBody RegisterUserRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<JwtAuthenticationResponse> login(
            @RequestBody LoginUserRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }
}
