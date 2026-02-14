package com.example.WebChat.UtilsConfigs;

import com.example.WebChat.DTO.CustomPrincipal;
import com.example.WebChat.Service.CustomUserDetailsService;
import com.example.WebChat.Service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication filter that runs once per HTTP request.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *     <li>Inspect the {@code Authorization} header for a {@code Bearer &lt;token&gt;} value.</li>
 *     <li>Extract and validate the JWT using {@link JwtService}.</li>
 *     <li>Load the corresponding user via {@link CustomUserDetailsService}.</li>
 *     <li>Populate the Spring Security {@link SecurityContextHolder} with an authenticated
 *         {@link UsernamePasswordAuthenticationToken} if the token is valid.</li>
 * </ul>
 *
 * <p>
 * This filter does not handle errors explicitly (e.g. expired/invalid token) –
 * in such cases the request simply proceeds as unauthenticated and will be
 * rejected later by the security configuration or controller annotations.
 * </p>
 *
 * <p>
 * Extends {@link OncePerRequestFilter} instead of a raw {@code Filter} to ensure
 * it runs only once per request, even in async/error dispatch scenarios.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    /**
     * Core filter logic, invoked once per request.
     *
     * <p>Flow:</p>
     * <ol>
     *     <li>Read the {@code Authorization} header.</li>
     *     <li>If it starts with {@code Bearer }, extract the token substring.</li>
     *     <li>Use {@link JwtService} to extract a username from the token.</li>
     *     <li>If no authentication is already present in the context, load the user
     *         and validate the token against that user.</li>
     *     <li>On success, set an authenticated {@link UsernamePasswordAuthenticationToken}
     *         in the {@link SecurityContextHolder}.</li>
     *     <li>Always delegate to the rest of the filter chain at the end.</li>
     * </ol>
     */
    @Override
    public void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // 1. Guard Clauses (Έλεγχοι στην αρχή)
        if (authHeader == null || !authHeader.startsWith("Bearer ") || request.getRequestURI().endsWith("/actuator/prometheus")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);
        log.info("The JWT: {}" , jwt);

        // 2. Stateless Auth Flow
        if (jwtService.isTokenValid(jwt)) {
            String username = jwtService.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UsernamePasswordAuthenticationToken authToken = buildAuthToken(jwt, username, request);

                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("Stateless authentication set for user: {}", username);
            }
        }

        filterChain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken buildAuthToken(String jwt, String username, HttpServletRequest request) {
        Long userId = jwtService.extractUserId(jwt);

        var authorities = jwtService.extractAuthorities(jwt);

        CustomPrincipal principal = new CustomPrincipal(
                userId,
                username,
                null,  // Password hash not needed
                false, // Stealth mode handled by Redis/Service
                true,  // Enabled
                authorities
        );

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities
        );

        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authToken;
    }
}
