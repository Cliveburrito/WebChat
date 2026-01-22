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
 *
 * <p><b>Why BCrypt?</b></p>
 * <ul>
 *     <li>Salt is automatically included → prevents rainbow table attacks.</li>
 *     <li>Configurable workload factor → protects against brute-force attempts.</li>
 *     <li>Industry standard for secure password storage.</li>
 * </ul>
 */
@Configuration
public class SecurityBeansConfig {

    /**
     * Provides a password encoder bean using BCrypt hashing.
     *
     * <p>
     * Spring Security will automatically use this bean whenever password
     * encryption or comparison is required — for example during:
     * </p>
     * <ul>
     *     <li>User registration (password hashing)</li>
     *     <li>Login authentication (password matching)</li>
     *     <li>Manual encoding inside services if needed</li>
     * </ul>
     *
     * @return a {@link BCryptPasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Strong secure hashing algorithm
    }
}
