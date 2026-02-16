package com.example.WebChat;

import com.example.WebChat.Exception.RateLimitExceededException;
import com.example.WebChat.Service.RateLimiterService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    ProxyManager<String> proxyManager;
    @Mock
    RemoteBucketBuilder<String> builder;

    private RateLimiterService rateLimiterService;

    // Simulate Redis: one bucket per key
    private final Map<String, Bucket> bucketsByKey = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        when(proxyManager.builder()).thenReturn(builder);

        when(builder.build(anyString(), any(Supplier.class)))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(0);
                    Supplier<BucketConfiguration> supplier = inv.getArgument(1);
                    BucketConfiguration config = supplier.get();

                    Bucket proxyMock = mock(Bucket.class, withSettings().extraInterfaces(io.github.bucket4j.distributed.BucketProxy.class));

                    Bucket realLocalBucket = bucketsByKey.computeIfAbsent(key, k -> {
                        var lb = Bucket.builder();

                        // ✅ ΣΩΣΤΟ: Iterate over the array and add each Bandwidth individually
                        for (Bandwidth b : config.getBandwidths()) {
                            lb.addLimit(b);
                        }

                        return lb.build();
                    });

                    when(proxyMock.tryConsumeAndReturnRemaining(anyLong())).thenAnswer(i ->
                            realLocalBucket.tryConsumeAndReturnRemaining(i.getArgument(0, Long.class))
                    );

                    return proxyMock;
                });

        rateLimiterService = new RateLimiterService(proxyManager);
    }

    @AfterEach
    void tearDown() {
        bucketsByKey.clear();
    }

    @Nested
    @DisplayName("Auth rate limiting")
    class AuthTests {

        @Test
        @DisplayName("Allows first 5 auth attempts, blocks 6th (throws)")
        void shouldLimitAuthAttempts() {
            String ip = "192.168.1.1";

            for (int i = 0; i < 5; i++) {
                assertDoesNotThrow(() -> rateLimiterService.consumeAuthOrThrow(ip));
            }

            assertThrows(RateLimitExceededException.class,
                    () -> rateLimiterService.consumeAuthOrThrow(ip));
        }

        @Test
        @DisplayName("Different IPs have independent limits")
        void shouldHaveIndependentLimitsPerIp() {
            String ip1 = "192.168.1.1";
            String ip2 = "1.1.1.1";

            for (int i = 0; i < 5; i++) {
                rateLimiterService.consumeAuthOrThrow(ip1);
            }
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimiterService.consumeAuthOrThrow(ip1));

            assertDoesNotThrow(() -> rateLimiterService.consumeAuthOrThrow(ip2));
        }
    }

    @Nested
    @DisplayName("Message rate limiting")
    class MessageTests {

        @Test
        @DisplayName("Allows first 5 messages, blocks 6th (throws)")
        void shouldLimitMessagesAfterCapacityExceeded() {
            Long userId = 1L;
            String username = "testuser";
            Long chatId = 100L;

            for (int i = 0; i < 5; i++) {
                assertDoesNotThrow(() ->
                        rateLimiterService.consumeMessageOrThrow(userId, username, chatId));
            }

            assertThrows(RateLimitExceededException.class,
                    () -> rateLimiterService.consumeMessageOrThrow(userId, username, chatId));
        }

        @Test
        @DisplayName("Different users have independent message limits")
        void shouldHaveIndependentMessageLimitsPerUser() {
            Long u1 = 1L;
            Long u2 = 2L;
            Long chatId = 100L;

            for (int i = 0; i < 5; i++) {
                rateLimiterService.consumeMessageOrThrow(u1, "u1", chatId);
            }
            assertThrows(RateLimitExceededException.class,
                    () -> rateLimiterService.consumeMessageOrThrow(u1, "u1", chatId));

            assertDoesNotThrow(() -> rateLimiterService.consumeMessageOrThrow(u2, "u2", chatId));
        }

        @Test
        @DisplayName("Same user, different chatId still shares limit (because key is per-user)")
        void shouldShareLimitAcrossChatsForSameUser() {
            Long userId = 1L;

            // 5 tokens total regardless of chatId because key = rl:msg:user:<id>
            for (int i = 0; i < 5; i++) {
                rateLimiterService.consumeMessageOrThrow(userId, "u", 100L);
            }

            assertThrows(RateLimitExceededException.class,
                    () -> rateLimiterService.consumeMessageOrThrow(userId, "u", 200L));
        }
    }

    @Nested
    @DisplayName("File upload rate limiting")
    class FileTests {

        @Test
        @DisplayName("Allows total 5 tokens, blocks when exceeded (multi-file consumes multiple tokens)")
        void shouldLimitUploadsByFilesCount() {
            Long userId = 1L;
            String username = "testuser";
            Long convId = 10L;
            Long messageId = 99L;

            assertDoesNotThrow(() ->
                    rateLimiterService.consumeFileOrThrow(userId, username, 3, convId, messageId));

            assertDoesNotThrow(() ->
                    rateLimiterService.consumeFileOrThrow(userId, username, 2, convId, messageId));

            assertThrows(RateLimitExceededException.class, () ->
                    rateLimiterService.consumeFileOrThrow(userId, username, 1, convId, messageId));
        }

        @Test
        @DisplayName("Different users have independent file limits")
        void shouldHaveIndependentFileLimits() {
            Long u1 = 1L;
            Long u2 = 2L;

            assertDoesNotThrow(() ->
                    rateLimiterService.consumeFileOrThrow(u1, "u1", 5, 10L, 99L));

            assertThrows(RateLimitExceededException.class, () ->
                    rateLimiterService.consumeFileOrThrow(u1, "u1", 1, 10L, 99L));

            assertDoesNotThrow(() ->
                    rateLimiterService.consumeFileOrThrow(u2, "u2", 1, 10L, 99L));
        }
    }
}
