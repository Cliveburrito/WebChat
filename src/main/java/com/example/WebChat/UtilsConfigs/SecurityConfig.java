package com.example.WebChat.UtilsConfigs;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

/**
 * Main Spring Security configuration for the application.
 *
 * <p>This class defines:</p>
 * <ul>
 *     <li>Stateless session handling for JWT-based authentication.</li>
 *     <li>Routes that are publicly accessible (permitAll).</li>
 *     <li>Insertion of a custom JWT filter in the security chain.</li>
 * </ul>
 *
 * <p>Because JWT replaces session-based login, no session is stored on the server,
 * and each request must include a valid token.</p>
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthFilter;

    /**
     * Configures application-level HTTP security rules.
     *
     * <p>Key elements:</p>
     * <ul>
     *     <li>CSRF disabled → not needed for token-based security.</li>
     *     <li>Sessions disabled → every request must carry a token.</li>
     *     <li>Public routes defined via {@code permitAll()}.</li>
     *     <li>A JWT filter is added before Spring's username/password filter.</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // Disable CSRF because the app uses stateless JWT authentication
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // Do not create or use HTTP sessions , every request must authenticate via jwt
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Publicly accessible routes (no token required)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/favicon.ico",
                           "/actuator/**",
                                "/api/auth/**",
                                "/ws/**"
                        ).permitAll()
                        // All other routes require authentication
                        .anyRequest().authenticated()
                )

                // Add our JWT filter before Spring Security's default authentication filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Exposes the {@link AuthenticationManager} as a Spring Bean.
     *
     * <p>
     * Required for user login authentication in Spring Security,
     * especially when manually authenticating credentials in the AuthService.
     * </p>
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
