package com.example.WebChat.Service;

import com.example.WebChat.DTO.*;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public JwtAuthenticationResponse register(RegisterUserRequest request) {

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

    public JwtAuthenticationResponse login(LoginUserRequest request) {

        // αφήνουμε το AuthenticationManager να τσεκάρει password
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

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

        String token = jwtService.generateToken(userDetails);

        return new JwtAuthenticationResponse(token, userResponse);
    }
}
