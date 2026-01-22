package com.example.WebChat.Controller;

import com.example.WebChat.DTO.LoginUserRequest;
import com.example.WebChat.DTO.RegisterUserRequest;
import com.example.WebChat.DTO.UserResponse;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Repository.UserRepository;
import com.example.WebChat.Service.UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;


    @GetMapping("/getall") // Πρόσθεσε αυτό εδώ
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
                .map(u -> new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getAvatarUrl()))
                .toList();
    }

    @GetMapping("/getuser/{username}")
    public UserResponse getUser(@PathVariable String username) {
        Optional<User> user =  userRepository.findByUsername(username);
        User u = user.get();
        return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getAvatarUrl());
    }
}
