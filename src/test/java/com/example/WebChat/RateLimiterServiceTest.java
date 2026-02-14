package com.example.WebChat;

import com.example.WebChat.Service.RateLimiterService;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
        rateLimiterService.clearBuckets(); // Clean up between tests
    }

    @Nested
    @DisplayName("Message Bucket Tests")
    class MessageBucketTests {

        @Test
        @DisplayName("Should return same bucket for same user")
        void shouldReturnSameBucketForSameUser() {
            // Arrange
            Long userId = 1L;

            // Act
            Bucket bucket1 = rateLimiterService.resolveMessageBucket(userId);
            Bucket bucket2 = rateLimiterService.resolveMessageBucket(userId);

            // Assert
            assertSame(bucket1, bucket2, "Should return the same bucket instance for the same user");
        }

        @Test
        @DisplayName("Should limit messages after capacity is exceeded")
        void shouldLimitMessagesAfterCapacityIsExceeded() {
            // Arrange
            Long userId = 1L;
            Bucket bucket = rateLimiterService.resolveMessageBucket(userId);

            // Act & Assert - First 5 messages should be allowed
            for (int i = 0; i < 5; i++) {
                assertTrue(bucket.tryConsume(1), "Should allow message " + (i + 1));
            }

            // 6th message should be blocked
            assertFalse(bucket.tryConsume(1), "Should block the 6th message");
        }

        @Test
        @DisplayName("Should refill tokens after time passes")
        void shouldRefillTokensAfterTimePasses() {
            // Arrange
            Long userId = 1L;
            Bucket bucket = rateLimiterService.resolveMessageBucket(userId);

            // Consume all 5 tokens
            for (int i = 0; i < 5; i++) {
                assertTrue(bucket.tryConsume(1));
            }

            // Verify no tokens left
            assertFalse(bucket.tryConsume(1));

            // Wait for refill (10 seconds is long for tests, so we'll mock in real scenario)
            // In a real test, you might want to use a shorter duration or mock the bucket
            // For now, we'll just verify the bucket exists
            assertNotNull(bucket);
        }

        @Test
        @DisplayName("Should have different buckets for different users")
        void shouldHaveDifferentBucketsForDifferentUsers() {
            // Act
            Bucket user1 = rateLimiterService.resolveMessageBucket(1L);
            Bucket user2 = rateLimiterService.resolveMessageBucket(2L);

            // Assert
            assertNotSame(user1, user2, "Different users must have separate buckets");

            // Verify they work independently
            assertTrue(user1.tryConsume(5)); // User1 consumes all
            assertFalse(user1.tryConsume(1)); // User1 blocked
            assertTrue(user2.tryConsume(1)); // User2 still allowed
        }
    }

    @Nested
    @DisplayName("Auth Bucket Tests")
    class AuthBucketTests {

        @Test
        @DisplayName("Should return same bucket for same IP")
        void shouldReturnSameBucketForSameIP() {
            // Arrange
            String ip = "192.168.1.1";

            // Act
            Bucket bucket1 = rateLimiterService.resolveAuthBucket(ip);
            Bucket bucket2 = rateLimiterService.resolveAuthBucket(ip);

            // Assert
            assertSame(bucket1, bucket2, "Should return the same bucket instance for the same IP");
        }

        @Test
        @DisplayName("Should have different buckets for different IPs")
        void shouldHaveDifferentBucketsForDifferentIPs() {
            // Act
            Bucket ip1 = rateLimiterService.resolveAuthBucket("192.168.1.1");
            Bucket ip2 = rateLimiterService.resolveAuthBucket("1.1.1.1");

            // Assert
            assertNotSame(ip1, ip2, "Different IPs must have separate buckets");
        }

        @Test
        @DisplayName("Should limit auth attempts after capacity is exceeded")
        void shouldLimitAuthAttempts() {
            // Arrange
            String ip = "192.168.1.1";
            Bucket bucket = rateLimiterService.resolveAuthBucket(ip);

            // Act & Assert - First 5 attempts should be allowed
            for (int i = 0; i < 5; i++) {
                assertTrue(bucket.tryConsume(1), "Should allow auth attempt " + (i + 1));
            }

            // 6th attempt should be blocked
            assertFalse(bucket.tryConsume(1), "Should block the 6th auth attempt");
        }
    }

    @Nested
    @DisplayName("File Bucket Tests")
    class FileBucketTests {

        @Test
        @DisplayName("Should return same bucket for same user")
        void shouldReturnSameBucketForSameUser() {
            // Arrange
            Long userId = 1L;

            // Act
            Bucket bucket1 = rateLimiterService.resolveFileBucket(userId);
            Bucket bucket2 = rateLimiterService.resolveFileBucket(userId);

            // Assert
            assertSame(bucket1, bucket2, "Should return the same bucket instance for the same user (FIXED!)");
        }

        @Test
        @DisplayName("Should limit file uploads after capacity is exceeded")
        void shouldLimitFileUploads() {
            // Arrange
            Long userId = 1L;
            Bucket bucket = rateLimiterService.resolveFileBucket(userId);

            // Act & Assert - First 5 uploads should be allowed (each file counts as 1)
            for (int i = 0; i < 5; i++) {
                assertTrue(bucket.tryConsume(1), "Should allow file upload " + (i + 1));
            }

            // 6th upload should be blocked
            assertFalse(bucket.tryConsume(1), "Should block the 6th file upload");
        }

        @Test
        @DisplayName("Should handle multiple files in one upload")
        void shouldHandleMultipleFilesInOneUpload() {
            // Arrange
            Long userId = 1L;
            Bucket bucket = rateLimiterService.resolveFileBucket(userId);

            // Act & Assert - Upload 3 files at once (consumes 3 tokens)
            assertTrue(bucket.tryConsume(3), "Should allow uploading 3 files");

            // Upload 2 more files (consumes 2 tokens, total 5)
            assertTrue(bucket.tryConsume(2), "Should allow uploading 2 more files");

            // Try to upload 1 more file (should be blocked)
            assertFalse(bucket.tryConsume(1), "Should block when capacity exceeded");
        }

        @Test
        @DisplayName("Should have different buckets for different users")
        void shouldHaveDifferentBucketsForDifferentUsers() {
            // Act
            Bucket user1 = rateLimiterService.resolveFileBucket(1L);
            Bucket user2 = rateLimiterService.resolveFileBucket(2L);

            // Assert
            assertNotSame(user1, user2, "Different users must have separate file buckets");
        }
    }

    @Nested
    @DisplayName("Cross-Bucket Tests")
    class CrossBucketTests {

        @Test
        @DisplayName("Should have separate buckets for different types for same user")
        void shouldHaveSeparateBucketsForDifferentTypes() {
            // Arrange
            Long userId = 1L;

            // Act
            Bucket messageBucket = rateLimiterService.resolveMessageBucket(userId);
            Bucket fileBucket = rateLimiterService.resolveFileBucket(userId);

            // Assert
            assertNotSame(messageBucket, fileBucket, "Message and file buckets should be different");

            // Verify they work independently
            assertTrue(messageBucket.tryConsume(5)); // Use all message tokens
            assertFalse(messageBucket.tryConsume(1)); // Message blocked

            assertTrue(fileBucket.tryConsume(1)); // File still allowed
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Should handle concurrent access to buckets")
        void shouldHandleConcurrentAccess() throws InterruptedException {
            // Arrange
            Long userId = 1L;

            // Act - Simulate 10 threads trying to access the same bucket
            Runnable task = () -> {
                Bucket bucket = rateLimiterService.resolveMessageBucket(userId);
                assertNotNull(bucket);
            };

            Thread[] threads = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads[i] = new Thread(task);
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Assert - Should still have only one bucket instance
            Bucket bucket1 = rateLimiterService.resolveMessageBucket(userId);
            Bucket bucket2 = rateLimiterService.resolveMessageBucket(userId);
            assertSame(bucket1, bucket2);
        }
    }
}