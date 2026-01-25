package com.example.WebChat.UtilsConfigs;

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
import org.springframework.security.core.userdetails.UserDetails;
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
    private final CustomUserDetailsService userDetailsService;

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
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Debug marker: verify filter is being executed
        log.info("JwtAuthenticationFilter invoked for URI: {}", request.getRequestURI());

        // Read Authorization header (expected format: "Bearer <jwt>")
        final String authHeader = request.getHeader("Authorization");
        String jwt;
        String username;

        // If no Authorization header or does not start with "Bearer ", skip JWT processing
        // The request continues unauthenticated (may still access public endpoints)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract raw JWT value from header (strip "Bearer " prefix)
        jwt = authHeader.substring(7);
        log.info("jwt = {}", jwt);

        // Extract username from token (implementation usually also checks signature & expiration)
        username = jwtService.extractUsername(jwt);

        // Proceed only if:
        //  - a username was successfully extracted, AND
        //  - no authentication has yet been set in the security context
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Load user details from database or other backend source.
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Validate the JWT against the loaded user, meaning username match, expiration, signature
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // Create an authenticated token for the current user
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,                 // principal
                                null,                        // credentials (not stored)
                                userDetails.getAuthorities() // roles/authorities
                        );

                // Attach additional request details, IP, session ID, etc.
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Store the Authentication object in the security context
                // From this point on, controllers and other components can retrieve:
                // SecurityContextHolder.getContext().getAuthentication()
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
            // If token is invalid, we do nothing , the request proceeds without authentication
        }

        // Continue with the rest of the filter chain, regardless of auth outcome.
        filterChain.doFilter(request, response);
    }
}
