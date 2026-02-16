package com.example.WebChat.UtilsConfigs;

import com.example.WebChat.Exception.RateLimitExceededException;
import com.example.WebChat.Service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        boolean isAuthEndpoint =
                "POST".equals(method) &&
                        ("/api/auth/login".equals(path) || "/api/auth/register".equals(path));

        if (!isAuthEndpoint) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);

        try {
            // 1 token per auth attempt
            rateLimiterService.consumeAuthOrThrow(ip);
            filterChain.doFilter(request, response);

        } catch (RateLimitExceededException ex) {
            // ✅ log who hit it
            log.warn("RATE_LIMIT auth ip={} path={} ua={}", ip, path, request.getHeader("User-Agent"));
            throw ex; // GlobalExceptionHandler θα επιστρέψει 429
        }
    }

    private String getClientIp(HttpServletRequest request) {
        log.info("xff : {}", request.getHeader("X-Forwarded-For"));
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
