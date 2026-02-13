package com.example.WebChat;

import com.example.WebChat.Service.RateLimiterService;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnSameBucketForSameUser() {
        // Arrange
        String username = "mitsos";

        // Act
        Bucket bucket1 = rateLimiterService.resolveMessageBucket(username);
        Bucket bucket2 = rateLimiterService.resolveMessageBucket(username);

        // Assert
        assertSame(bucket1, bucket2, "Should return the same bucket instance for the same user");
    }

    @Test
    void shouldLimitMessagesAfterCapacityIsExceeded() {
        // Arrange
        String username = "mitsos";
        Bucket bucket = rateLimiterService.resolveMessageBucket(username);

        // Act & Assert
        // capacity is 5 so consume 5
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryConsume(1), "Should allow message " + (i + 1));
        }

        // 6th message gots to go
        assertFalse(bucket.tryConsume(1), "Should block the 6th message");
    }

    @Test
    void shouldHaveDifferentBucketsForDifferentIPs() {
        // Act
        Bucket ip1 = rateLimiterService.resolveAuthBucket("192.168.1.1");
        Bucket ip2 = rateLimiterService.resolveAuthBucket("1.1.1.1");

        // Assert
        assertNotSame(ip1, ip2, "Different IPs must have separate buckets");
    }
}
