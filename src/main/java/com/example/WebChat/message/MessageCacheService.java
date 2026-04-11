package com.example.WebChat.message;

import com.example.WebChat.message.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fory.ThreadSafeFory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageCacheService {
    private static final String CACHE_VERSION = "v3";

    private final StringRedisTemplate stringRedisTemplate;
    @Qualifier("furyRedisTemplate")
    private final RedisTemplate<String, byte[]> furyRedisTemplate;
    private final ThreadSafeFory fury;
    private final RedisScript<Long> atomicTrimScript;

    public String getIndexKey(Long conversationId) {
        return "chat:" + CACHE_VERSION + ":index:" + conversationId;
    }

    public String getDataKey(Long conversationId) {
        return "chat:" + CACHE_VERSION + ":data:" + conversationId;
    }

    public void updateMessageInCache(Long conversationId, ChatMessageResponse response) {
        String indexKey = getIndexKey(conversationId);
        String dataKey = getDataKey(conversationId);

        try {
            String msgIdStr = response.id().toString();
            byte[] payload = fury.serialize(response);

            furyRedisTemplate.opsForHash().put(dataKey, msgIdStr, payload);

            double score = (double) response.createdAt().toEpochMilli();
            stringRedisTemplate.opsForZSet().add(indexKey, msgIdStr, score);

            stringRedisTemplate.execute(atomicTrimScript, List.of(indexKey, dataKey), "100");

            stringRedisTemplate.expire(indexKey, Duration.ofDays(7));
            stringRedisTemplate.expire(dataKey, Duration.ofDays(7));
        } catch (Exception e) {
            log.warn("Cache update failed for msg {}", response.id(), e);
        }
    }

    public void refreshRedisCache(Long conversationId, List<ChatMessageResponse> data) {
        String indexKey = getIndexKey(conversationId);
        String dataKey = getDataKey(conversationId);

        try {
            Map<String, byte[]> hashes = new HashMap<>();
            Set<ZSetOperations.TypedTuple<String>> zsetEntries = new LinkedHashSet<>();

            for (ChatMessageResponse response : data) {
                String msgIdStr = response.id().toString();
                byte[] payload = fury.serialize(response);
                double score = (double) response.createdAt().toEpochMilli();

                hashes.put(msgIdStr, payload);
                zsetEntries.add(ZSetOperations.TypedTuple.of(msgIdStr, score));
            }

            stringRedisTemplate.delete(List.of(indexKey, dataKey));

            if (!hashes.isEmpty()) {
                furyRedisTemplate.opsForHash().putAll(dataKey, hashes);
            }

            if (!zsetEntries.isEmpty()) {
                stringRedisTemplate.opsForZSet().add(indexKey, zsetEntries);
            }

            stringRedisTemplate.expire(indexKey, Duration.ofDays(7));
            stringRedisTemplate.expire(dataKey, Duration.ofDays(7));

            log.info("Redis warm-up completed for conv {}", conversationId);
        } catch (Exception e) {
            log.warn("Redis warm-up failed for conv {}", conversationId, e);
        }
    }

    public void evictMessageCache(Long conversationId) {
        stringRedisTemplate.delete(List.of(getIndexKey(conversationId), getDataKey(conversationId)));
    }
}
