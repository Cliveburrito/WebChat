package com.example.WebChat.Service;

import com.example.WebChat.shared.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final ProxyManager<String> proxyManager;

    // --- Policies (no deprecated Refill/Bandwidth.classic) ---
    private static final Supplier<BucketConfiguration> AUTH_CONF = () ->
            BucketConfiguration.builder()
                    .addLimit(limit -> limit
                            .capacity(5)
                            .refillGreedy(5, Duration.ofMinutes(1)))
                    .build();

    private static final Supplier<BucketConfiguration> MSG_CONF = () ->
            BucketConfiguration.builder()
                    .addLimit(limit -> limit
                            .capacity(5)
                            .refillGreedy(5, Duration.ofSeconds(1)))
                    .build();

    private static final Supplier<BucketConfiguration> FILE_CONF = () ->
            BucketConfiguration.builder()
                    .addLimit(limit -> limit
                            .capacity(5)
                            .refillGreedy(5, Duration.ofMinutes(1)))
                    .build();

    public void consumeAuthOrThrow(String ip) {
        String key = "rl:auth:ip:" + ip;
        consumeOrThrow(key, AUTH_CONF, 1,
                "type=auth ip=" + ip);
    }

    public void consumeMessageOrThrow(Long userId, String username, Long chatId) {
        String key = "rl:msg:user:" + userId;
        consumeOrThrow(key, MSG_CONF, 1,
                "type=message userId=" + userId + " username=" + username + " chatId=" + chatId);
    }

    public void consumeFileOrThrow(Long userId, String username, int filesCount, Long conversationId, Long messageId) {
        String key = "rl:file:user:" + userId;
        consumeOrThrow(key, FILE_CONF, filesCount,
                "type=upload userId=" + userId + " username=" + username +
                        " files=" + filesCount + " convId=" + conversationId + " messageId=" + messageId);
    }

    private void consumeOrThrow(String key,
                                Supplier<BucketConfiguration> config,
                                int tokens,
                                String logContext) {

        Bucket bucket = proxyManager.builder().build(key, config);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(tokens);

        if (probe.isConsumed()) return;

        long retrySeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());

        // ✅ ΕΝΑ log για ΟΛΑ
        log.warn("RATE_LIMIT {} retryIn={}s key={}", logContext, retrySeconds, key);

        throw new RateLimitExceededException("Rate limit exceeded. Retry in ~" + retrySeconds + "s.");
    }
}


