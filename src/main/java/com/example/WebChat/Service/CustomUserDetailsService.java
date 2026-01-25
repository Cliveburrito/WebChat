package com.example.WebChat.Service;

import com.example.WebChat.Repository.UserRepository;
import lombok.RequiredArgsConstructor; // ← Lombok auto-generates constructor (no boilerplate!)
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
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    /**
     * Called automatically by Spring Security during authentication
     *  IMPORTANT: This method:
     * - Must throw UsernameNotFoundException if user doesn’t exist
     * - Must return a UserDetails — NOT the user  entity directly
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // Fetch user from DB
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Convert the entity User to Spring Security's UserDetails
        // Why? Spring doesn’t know your entity , it only knows UserDetails contract.
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities("USER")
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}