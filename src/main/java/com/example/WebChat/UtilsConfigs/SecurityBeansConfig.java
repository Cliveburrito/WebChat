package com.example.WebChat.UtilsConfigs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provides reusable Security-related beans for the application.
 *
 * <p>
 * Currently, this configuration exposes a {@link PasswordEncoder} implementation
 * that is used across the authentication system for hashing and verifying passwords.
 * </p>
 */
@Configuration
public class SecurityBeansConfig {

    /**
     * Provides a password encoder bean using BCrypt hashing.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Strong secure hashing algorithm
    }
}
