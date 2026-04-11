package com.example.WebChat.conversation;


import com.example.WebChat.conversation.ConvMembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipGuard {
    private final ConvMembershipRepository repository;
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "user:memberships:";

    public boolean isMember(Long userId, Long conversationId) {
        String key = KEY_PREFIX + userId;

        Boolean isCached = redisTemplate.opsForSet().isMember(key, conversationId.toString());

        if (Boolean.TRUE.equals(isCached)) {
            log.info("Membership cache hit!");
            return true;
        }


        boolean exists = repository.existsByUserIdAndConvId(userId, conversationId);

        if (exists) {
            redisTemplate.opsForSet().add(key, conversationId.toString());
            redisTemplate.expire(key, Duration.ofHours(1));
        }

        return exists;
    }

    public void evictCache(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
        log.info("Evicted membership cache for user {}", userId);
    }
}