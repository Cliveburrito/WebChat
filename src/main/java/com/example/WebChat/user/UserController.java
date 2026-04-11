package com.example.WebChat.user;

import com.example.WebChat.auth.dto.CustomPrincipal;
import com.example.WebChat.user.dto.UserResponse;
import com.example.WebChat.shared.ResourceNotFoundException;
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
                .map(this::toDirectoryUser)
                .toList();
    }

    @GetMapping("/getuser/{username}")
    public UserResponse getUser(@PathVariable String username) {
        User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return toDirectoryUser(u);
    }

    @PatchMapping("/me/stealth")
    public ResponseEntity<Void> updateStealthMode(
            @AuthenticationPrincipal CustomPrincipal principal,
            @RequestParam boolean enabled) {

        userService.toggleStealthMode(principal.id(), principal.username(), enabled);
        return ResponseEntity.ok().build();
    }

    private UserResponse toDirectoryUser(User user) {
        return new UserResponse(user.getId(), user.getUsername(), null, user.getAvatarUrl());
    }
}
