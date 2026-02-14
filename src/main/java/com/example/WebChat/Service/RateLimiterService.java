package com.example.WebChat.Service;

import io.github.bucket4j.Bucket;
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

    public Bucket resolveMessageBucket(Long userId) {
        return buckets.computeIfAbsent("MSG_" + userId , k -> Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(5)
                        .refillGreedy(5, Duration.ofSeconds(1)))
                .build());
    }

    public Bucket resolveFileBucket(Long userId) {
        // 2. computeIfAbsent: Αν υπάρχει το δίνει, αν όχι το φτιάχνει ΜΙΑ φορά
        return buckets.computeIfAbsent("FILE" + userId, key ->
                Bucket.builder()
                        .addLimit(limit -> limit.capacity(5).refillGreedy(5, Duration.ofMinutes(1)))
                        .build()
        );
    }

    public void clearBuckets() {
        buckets.clear();
    }
}