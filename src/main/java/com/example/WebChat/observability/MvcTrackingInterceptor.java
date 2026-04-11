package com.example.WebChat.observability;

import com.example.WebChat.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class MvcTrackingInterceptor implements HandlerInterceptor {
    private static final String MVC_START_NANOS_ATTRIBUTE = MvcTrackingInterceptor.class.getName() + ".startNanos";

    private final AppProperties appProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(MVC_START_NANOS_ATTRIBUTE, System.nanoTime());
        if (isEnabled(request)) {
            log.debug(
                    "mvc_pre_handle requestId={} method={} path={} handler={}",
                    RequestTracking.currentRequestId(),
                    request.getMethod(),
                    request.getRequestURI(),
                    handler.getClass().getSimpleName()
            );
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!isEnabled(request)) {
            return;
        }

        long startNanos = request.getAttribute(MVC_START_NANOS_ATTRIBUTE) instanceof Long value
                ? value
                : System.nanoTime();
        long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

        log.debug(
                "mvc_after_completion requestId={} method={} path={} status={} durationMs={} exception={}",
                RequestTracking.currentRequestId(),
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                durationMs,
                ex == null ? "none" : ex.getClass().getSimpleName()
        );
    }

    private boolean isEnabled(HttpServletRequest request) {
        return appProperties.getTracking().isDetailedEnabled()
                && !"/actuator/prometheus".equals(request.getRequestURI());
    }
}
