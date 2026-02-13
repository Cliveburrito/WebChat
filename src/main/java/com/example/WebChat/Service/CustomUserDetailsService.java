package com.example.WebChat.Service;

import com.example.WebChat.DTO.CachedUser;
import com.example.WebChat.Repository.UserRepository;
import lombok.RequiredArgsConstructor; // ← Lombok auto-generates constructor (no boilerplate!)
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Custom implementation of Spring Security's
 * Purpose: When a user tries to log in, Spring Security calls this service to:
 * 1. Look up the user in the database by username (or email),
 * 2. Return a {@link UserDetails} object that Spring uses to:
 *    - Verify the password (by comparing hashes),
 *    - Authorize roles/permissions later (e.g., in @PreAuthorize).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Cacheable(value = "user_details", key = "#username")
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // This runs ONLY if the cache is empty for this username
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // We capture the data into our Record here
        return new CachedUser(
                user.getUsername(),
                user.getPasswordHash(),
                user.isStealthMode(),
                true
        );
    }
}