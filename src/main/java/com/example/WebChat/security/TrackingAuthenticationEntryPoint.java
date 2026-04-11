package com.example.WebChat.security;

import com.example.WebChat.observability.RequestTracking;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class TrackingAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        String reason = resolveReason(request);
        log.warn(
                "auth_failure requestId={} path={} status={} reason={}",
                RequestTracking.currentRequestId(),
                request.getRequestURI(),
                HttpStatus.UNAUTHORIZED.value(),
                reason
        );
        response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase());
    }

    private static String resolveReason(HttpServletRequest request) {
        Object explicitReason = request.getAttribute(RequestTracking.AUTH_FAILURE_REASON_ATTRIBUTE);
        if (explicitReason instanceof String reason && !reason.isBlank()) {
            return reason;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            return "missing_token";
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            return "auth_context_missing";
        }

        return "invalid_token";
    }
}
