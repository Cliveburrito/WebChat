package com.example.WebChat.security;

import com.example.WebChat.auth.dto.CustomPrincipal;
import com.example.WebChat.auth.CustomUserDetailsService;
import com.example.WebChat.auth.JwtService;
import com.example.WebChat.config.AppProperties;
import com.example.WebChat.observability.RequestTracking;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
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
    private final AppProperties appProperties;

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
        String path = request.getRequestURI();

        if (path.endsWith("/actuator/prometheus")) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean tokenPresent = authHeader != null && authHeader.startsWith("Bearer ");
        if (appProperties.getTracking().isDetailedEnabled()) {
            log.debug(
                    "auth_start requestId={} path={} tokenPresent={} authContextPresent={}",
                    RequestTracking.currentRequestId(),
                    path,
                    tokenPresent,
                    SecurityContextHolder.getContext().getAuthentication() != null
            );
        }

        // 1. Guard Clauses (Έλεγχοι στην αρχή)
        if (!tokenPresent) {
            request.setAttribute(RequestTracking.AUTH_FAILURE_REASON_ATTRIBUTE, "missing_token");
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);

        // 2. Stateless Auth Flow
        try {
            jwtService.validateTokenOrThrow(jwt);
            String username = jwtService.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UsernamePasswordAuthenticationToken authToken = buildAuthToken(jwt, username, request);

                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("auth_success requestId={} principal=userId:{}", RequestTracking.currentRequestId(), jwtService.extractUserId(jwt));
            }
        } catch (JwtException | IllegalArgumentException ex) {
            String reason = classifyAuthFailure(ex);
            request.setAttribute(RequestTracking.AUTH_FAILURE_REASON_ATTRIBUTE, reason);
            log.warn(
                    "auth_fail requestId={} path={} reason={} exception={}",
                    RequestTracking.currentRequestId(),
                    path,
                    reason,
                    ex.getClass().getSimpleName()
            );
        }

        filterChain.doFilter(request, response);
    }

    private static String classifyAuthFailure(Exception ex) {
        if (ex instanceof ExpiredJwtException) {
            return "expired_token";
        }
        if (ex instanceof SignatureException) {
            return "invalid_signature";
        }
        if (ex instanceof MalformedJwtException || ex instanceof UnsupportedJwtException || ex instanceof IllegalArgumentException) {
            return "invalid_token";
        }
        return "invalid_token";
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
