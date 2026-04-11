package com.example.WebChat.observability;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
@Component
public class TrackingLog {
    public void checkpoint(String name) {
        if (log.isDebugEnabled()) {
            log.debug("checkpoint event={} requestId={}", name, RequestTracking.currentRequestId());
        }
    }

    public void checkpoint(String name, String detailKey, Object detailValue) {
        if (log.isDebugEnabled()) {
            log.debug("checkpoint event={} requestId={} {}={}", name, RequestTracking.currentRequestId(), detailKey, detailValue);
        }
    }

    public <T> T time(String event, Supplier<T> supplier) {
        long start = System.nanoTime();
        checkpoint(event + ".start");
        try {
            return supplier.get();
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            if (log.isDebugEnabled()) {
                log.debug("checkpoint event={} requestId={} durationMs={}", event + ".end", MDC.get(RequestTracking.REQUEST_ID_MDC_KEY), durationMs);
            }
        }
    }
}
