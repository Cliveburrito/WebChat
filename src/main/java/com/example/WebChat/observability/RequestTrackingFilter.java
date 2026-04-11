package com.example.WebChat.observability;

import com.example.WebChat.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequestTrackingFilter extends OncePerRequestFilter {
    private final AppProperties appProperties;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        long startNanos = System.nanoTime();

        MDC.put(RequestTracking.REQUEST_ID_MDC_KEY, requestId);
        request.setAttribute(RequestTracking.START_NANOS_ATTRIBUTE, startNanos);
        response.setHeader(RequestTracking.REQUEST_ID_HEADER, requestId);

        AppProperties.Tracking tracking = appProperties.getTracking();
        boolean detailed = tracking.isDetailedEnabled() && !isPrometheus(request);
        String method = request.getMethod();
        String path = request.getRequestURI();

        try {
            if (detailed) {
                log.info(
                        "http_request_start requestId={} method={} path={} query={} remoteAddr={}",
                        requestId,
                        method,
                        path,
                        safeQuery(request.getQueryString()),
                        request.getRemoteAddr()
                );
            }

            filterChain.doFilter(request, response);
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            if (detailed) {
                log.info(
                        "http_request_complete requestId={} method={} path={} status={} durationMs={}",
                        requestId,
                        method,
                        path,
                        response.getStatus(),
                        durationMs
                );
            }

            if (detailed && durationMs > tracking.getSlowRequestThresholdMs()) {
                log.warn(
                        "http_request_slow requestId={} method={} path={} status={} durationMs={} principal={}",
                        requestId,
                        method,
                        path,
                        response.getStatus(),
                        durationMs,
                        SafePrincipal.currentSafeId()
                );
            }

            MDC.remove(RequestTracking.REQUEST_ID_MDC_KEY);
        }
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(RequestTracking.REQUEST_ID_HEADER);
        if (incoming != null && incoming.length() <= 128 && incoming.matches("[A-Za-z0-9._:-]+")) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    private static String safeQuery(String queryString) {
        return queryString == null || queryString.isBlank() ? "" : "present";
    }

    private static boolean isPrometheus(HttpServletRequest request) {
        return "/actuator/prometheus".equals(request.getRequestURI());
    }
}
