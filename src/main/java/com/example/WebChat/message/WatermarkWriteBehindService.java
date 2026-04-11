package com.example.WebChat.message;

import com.example.WebChat.config.AppProperties;
import com.example.WebChat.conversation.ConversationCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatermarkWriteBehindService {
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ConversationCacheService conversationCacheService;
    private final AppProperties appProperties;

    @Scheduled(fixedDelayString = "${application.watermarks.flush-fixed-delay-ms:5000}")
    public void flushDirtyWatermarks() {
        if (!appProperties.getWatermarks().isWriteBehindEnabled()) {
            return;
        }

        int batchSize = appProperties.getWatermarks().getFlushBatchSize();
        List<String> dirtyKeys = redisTemplate.opsForSet().pop(MessageService.DIRTY_WATERMARKS_KEY, batchSize);
        if (dirtyKeys == null || dirtyKeys.isEmpty()) {
            return;
        }

        List<WatermarkSnapshot> snapshots = dirtyKeys.stream()
                .map(this::snapshot)
                .filter(WatermarkSnapshot::hasAnyWatermark)
                .toList();

        if (snapshots.isEmpty()) {
            log.debug("Skipped watermark flush; {} dirty keys had no valid watermark values", dirtyKeys.size());
            return;
        }

        List<WatermarkSnapshot> deliveredSnapshots = snapshots.stream().filter(WatermarkSnapshot::hasDelivered).toList();
        List<WatermarkSnapshot> readSnapshots = snapshots.stream().filter(WatermarkSnapshot::hasRead).toList();

        try {
            batchUpdate("last_delivered_message_id", deliveredSnapshots, WatermarkSnapshot::deliveredMessageId);
            batchUpdate("last_read_message_id", readSnapshots, WatermarkSnapshot::readMessageId);
        } catch (RuntimeException ex) {
            snapshots.forEach(snapshot -> redisTemplate.opsForSet().add(MessageService.DIRTY_WATERMARKS_KEY, snapshot.key()));
            log.warn("Failed to flush {} dirty watermark keys; re-queued for next flush", snapshots.size(), ex);
            return;
        }

        Set<Long> changedUsers = new HashSet<>();
        int requeued = 0;
        for (WatermarkSnapshot snapshot : snapshots) {
            changedUsers.add(snapshot.userId());
            if (changedAfterSnapshot(snapshot)) {
                redisTemplate.opsForSet().add(MessageService.DIRTY_WATERMARKS_KEY, snapshot.key());
                requeued++;
            }
        }
        changedUsers.forEach(conversationCacheService::evictUserChats);

        log.info(
                "watermark_flush dirtyKeys={} validKeys={} deliveredUpdates={} readUpdates={} users={} requeued={}",
                dirtyKeys.size(),
                snapshots.size(),
                deliveredSnapshots.size(),
                readSnapshots.size(),
                changedUsers.size(),
                requeued
        );
    }

    private void batchUpdate(String columnName, List<WatermarkSnapshot> snapshots, java.util.function.Function<WatermarkSnapshot, Long> valueExtractor) {
        if (snapshots.isEmpty()) {
            return;
        }

        String sql = """
                UPDATE conversation_membership
                SET %s = ?
                WHERE user_id = ?
                  AND conversation_id = ?
                  AND (%s IS NULL OR %s < ?)
                """.formatted(columnName, columnName, columnName);

        List<Object[]> args = new ArrayList<>(snapshots.size());
        for (WatermarkSnapshot snapshot : snapshots) {
            Long messageId = valueExtractor.apply(snapshot);
            args.add(new Object[]{messageId, snapshot.userId(), snapshot.conversationId(), messageId});
        }

        jdbcTemplate.batchUpdate(sql, args);
    }

    private WatermarkSnapshot snapshot(String key) {
        WatermarkKey parsed = parseKey(key);
        if (parsed == null) {
            return new WatermarkSnapshot(key, null, null, null, null);
        }

        Object delivered = redisTemplate.opsForHash().get(key, "delivered");
        Object read = redisTemplate.opsForHash().get(key, "read");

        return new WatermarkSnapshot(
                key,
                parsed.conversationId(),
                parsed.userId(),
                parseLong(delivered),
                parseLong(read)
        );
    }

    private boolean changedAfterSnapshot(WatermarkSnapshot snapshot) {
        Long currentDelivered = parseLong(redisTemplate.opsForHash().get(snapshot.key(), "delivered"));
        Long currentRead = parseLong(redisTemplate.opsForHash().get(snapshot.key(), "read"));

        return !java.util.Objects.equals(currentDelivered, snapshot.deliveredMessageId())
                || !java.util.Objects.equals(currentRead, snapshot.readMessageId());
    }

    private static WatermarkKey parseKey(String key) {
        if (key == null || !key.startsWith(MessageService.WATERMARK_KEY_PREFIX)) {
            return null;
        }
        String[] parts = key.substring(MessageService.WATERMARK_KEY_PREFIX.length()).split(":");
        if (parts.length != 2) {
            return null;
        }
        Long conversationId = parseLong(parts[0]);
        Long userId = parseLong(parts[1]);
        if (conversationId == null || userId == null) {
            return null;
        }
        return new WatermarkKey(conversationId, userId);
    }

    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record WatermarkKey(Long conversationId, Long userId) {
    }

    private record WatermarkSnapshot(
            String key,
            Long conversationId,
            Long userId,
            Long deliveredMessageId,
            Long readMessageId
    ) {
        boolean hasAnyWatermark() {
            return conversationId != null && userId != null && (hasDelivered() || hasRead());
        }

        boolean hasDelivered() {
            return deliveredMessageId != null && deliveredMessageId > 0;
        }

        boolean hasRead() {
            return readMessageId != null && readMessageId > 0;
        }
    }
}
