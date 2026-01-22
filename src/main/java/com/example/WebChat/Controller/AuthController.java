package com.example.WebChat.Controller;

import com.example.WebChat.DTO.JwtAuthenticationResponse;
import com.example.WebChat.DTO.LoginUserRequest;
import com.example.WebChat.DTO.RegisterUserRequest;
import com.example.WebChat.Service.UserService;
import jakarta.servlet.http.HttpServletRequest;
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
            @RequestBody RegisterUserRequest registerRequest, HttpServletRequest req) {
        String ipAddress = req.getRemoteAddr();
        return ResponseEntity.ok(userService.register(registerRequest , ipAddress));
    }

    @PostMapping("/login")
    public ResponseEntity<JwtAuthenticationResponse> login(
            @RequestBody LoginUserRequest loginRequest, HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        return ResponseEntity.ok(userService.login(loginRequest, ipAddress));
    }
}
