package com.example.WebChat.Service;

import io.github.bucket4j.Bucket; // Note the direct Bucket import
import io.github.bucket4j.Bandwidth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RateLimiterService {

    // Store buckets in a thread-safe Map
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Bucket resolveAuthBucket(String ip) {
        return buckets.computeIfAbsent("AUTH_" + ip, k -> {
            log.info("Creating new AUTH bucket for IP: {}", ip);
            return Bucket.builder()
                    .addLimit(limit -> limit
                            .capacity(5)
                            .refillGreedy(5, Duration.ofMinutes(1)))
                    .build();
        });
    }

    public Bucket resolveMessageBucket(String username) {
        return buckets.computeIfAbsent("MSG_" + username , k -> {
            return Bucket.builder()
                    .addLimit(limit -> limit
                            .capacity(5)
                            .refillGreedy(2, Duration.ofSeconds(1)))
                    .build();
        });
    }
}