package com.example.WebChat.user;

import com.example.WebChat.attachment.FileSystemStorageService;
import com.example.WebChat.auth.dto.CustomPrincipal;
import com.example.WebChat.user.dto.ProfileUpdateRequest;
import com.example.WebChat.user.dto.UserResponse;
import com.example.WebChat.shared.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final FileSystemStorageService storageService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/getall")
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

    @GetMapping("/me")
    public UserResponse getMe(@AuthenticationPrincipal CustomPrincipal principal) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + principal.id()));
        return UserResponse.fromEntity(user);
    }

    @PatchMapping("/me/profile")
    public UserResponse updateProfile(
            @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest request) {

        UserResponse response = userService.updateProfile(principal.id(), request);
        messagingTemplate.convertAndSend("/topic/users", response);
        return response;
    }

    @PostMapping("/me/avatar")
    public UserResponse updateAvatar(
            @AuthenticationPrincipal CustomPrincipal principal,
            @RequestParam("file") MultipartFile file) {

        UserResponse response = userService.updateAvatar(principal.id(), file);
        messagingTemplate.convertAndSend("/topic/users", response);
        return response;
    }

    @GetMapping("/avatar/{storageName}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String storageName) {
        if (!userRepository.existsByAvatarUrl("/api/users/avatar/" + storageName)) {
            throw new ResourceNotFoundException("Avatar not found: " + storageName);
        }

        Resource resource = storageService.loadAsResource(storageName);
        String contentType = "application/octet-stream";
        try {
            String detected = Files.probeContentType(storageService.load(storageName));
            if (detected != null && !detected.isBlank()) {
                contentType = detected;
            }
        } catch (Exception ignored) {
            // Fall back to application/octet-stream if probing fails.
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }

    @PatchMapping("/me/stealth")
    public ResponseEntity<Void> updateStealthMode(
            @AuthenticationPrincipal CustomPrincipal principal,
            @RequestParam boolean enabled) {

        userService.toggleStealthMode(principal.id(), principal.username(), enabled);
        return ResponseEntity.ok().build();
    }

    private UserResponse toDirectoryUser(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                null,
                user.getDisplayName(),
                user.getBio(),
                user.getAvatarUrl(),
                user.getLastSeenAt()
        );
    }
}
