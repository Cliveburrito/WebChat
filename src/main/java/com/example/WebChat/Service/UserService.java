package com.example.WebChat.Service;

import com.example.WebChat.DTO.*;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Repository.UserRepository;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RateLimiterService rateLimiter;

    public JwtAuthenticationResponse register(RegisterUserRequest request, String ipAddress) {
        Bucket bucket = rateLimiter.resolveAuthBucket(ipAddress);

        if(!bucket.tryConsume(1)) {
            log.warn("Too many attempts from ip: {}", ipAddress);
            throw new RuntimeException("Too many requests. Please try again in a bit.");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already in use");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCreatedAt(Instant.now());

        User saved = userRepository.save(user);

        UserResponse userResponse = new UserResponse(
                saved.getId(),
                saved.getUsername(),
                saved.getEmail(),
                saved.getAvatarUrl()
        );

        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(saved.getUsername())
                .password(saved.getPasswordHash())
                .authorities("USER")
                .build();

        String token = jwtService.generateToken(userDetails);

        return new JwtAuthenticationResponse(token, userResponse);
    }

    public JwtAuthenticationResponse login(LoginUserRequest request, String ipAddress) {
        Bucket bucket = rateLimiter.resolveAuthBucket(ipAddress);

        if (!bucket.tryConsume(1)) {
            log.warn("Too many attempts from ip: {}", ipAddress);
            throw new RuntimeException("Too many requests. Please try again in a bit.");
        }

        // We let the authentication Manager check the password
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );
        log.info("Authentication successful");

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username"));

        UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl()
        );

        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities("USER")
                .build();

        String jwtToken = jwtService.generateToken(userDetails);
        log.info("JWT token issued successfully for user: {}", user.getUsername());

        return new JwtAuthenticationResponse(jwtToken, userResponse);
    }
}
