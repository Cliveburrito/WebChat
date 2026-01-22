package com.example.WebChat.Service;

import com.example.WebChat.Repository.UserRepository;
import lombok.RequiredArgsConstructor; // ← Lombok auto-generates constructor (no boilerplate!)
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Custom implementation of Spring Security's
 *
 * Purpose: When a user tries to log in, Spring Security calls this service to:
 * 1. Look up the user in the database by username (or email),
 * 2. Return a {@link UserDetails} object that Spring uses to:
 *    - Verify the password (by comparing hashes),
 *    - Authorize roles/permissions later (e.g., in @PreAuthorize).
 */
@Service
@RequiredArgsConstructor // ← Lombok generates: public CustomUserDetailsService(UserRepository userRepository) { this.userRepository = userRepository; }
public class CustomUserDetailsService implements UserDetailsService {

    // Injected via constructor (thanks to @RequiredArgsConstructor)
    // Best practice: final fields + constructor injection = immutable, testable, safe.
    private final UserRepository userRepository;

    /**
     * Called automatically by Spring Security during authentication (e.g., in UsernamePasswordAuthenticationFilter).
     *
     *  IMPORTANT: This method:
     * - Must throw {@link UsernameNotFoundException} if user doesn’t exist (to prevent timing attacks).
     * - Must return a {@link UserDetails} — NOT your own User entity directly.
     *
     * @param username the login identifier (e.g., "bob@example.com" or "bob")
     * @return a Spring {@link UserDetails} instance representing the user
     * @throws UsernameNotFoundException if no user with that username exists
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // Step 1: Fetch user from DB
        // userRepository.findByUsername() should return Optional<User>
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Step 2: Convert your domain User → Spring Security's UserDetails
        // Why? Spring doesn’t know your entity — it only knows UserDetails contract.
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())     //  "sub" in JWT, principal groupName
                .password(user.getPasswordHash())     //  MUST be already hashed (e.g., BCrypt)
                .authorities("USER")                  //  Roles/authorities (as strings like "ROLE_ADMIN", "USER")
                .accountExpired(false)                // (optional) default = true → block if expired
                .accountLocked(false)                 // (optional) default = true → block if locked
                .credentialsExpired(false)            // (optional) e.g., password expired
                .disabled(false)                      // (optional) manual disable
                .build();                             //  creates immutable UserDetails
    }
}