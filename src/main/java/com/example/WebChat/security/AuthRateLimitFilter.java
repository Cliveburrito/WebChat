package com.example.WebChat.security;

import com.example.WebChat.shared.RateLimitExceededException;
import com.example.WebChat.Service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> TRUSTED_PROXY_HEADERS = Set.of(
            "X-Forwarded-For",
            "X-Real-IP"
    );

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
            log.warn("RATE_LIMIT auth ip={} path={} ua={}", ip, path, request.getHeader("User-Agent"));

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                {"error":"too_many_requests","message":"%s"}
                """.formatted(escapeJson(ex.getMessage())));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        for (String header : TRUSTED_PROXY_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}
