package com.example.WebChat.Controller;

import com.example.WebChat.DTO.CustomPrincipal;
import com.example.WebChat.DTO.UserResponse;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Exception.ResourceNotFoundException;
import com.example.WebChat.Repository.UserRepository;
import com.example.WebChat.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping("/getall")
    @Cacheable(value = "global_users", key = "'all'")
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
                .map(u -> new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getAvatarUrl()))
                .toList();
    }

    @GetMapping("/getuser/{username}")
    public UserResponse getUser(@PathVariable String username) {
        User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getAvatarUrl());
    }

    @PatchMapping("/me/stealth")
    public ResponseEntity<Void> updateStealthMode(
            @AuthenticationPrincipal CustomPrincipal principal,
            @RequestParam boolean enabled) {

        userService.toggleStealthMode(principal.id(), principal.username(), enabled);
        return ResponseEntity.ok().build();
    }
}
