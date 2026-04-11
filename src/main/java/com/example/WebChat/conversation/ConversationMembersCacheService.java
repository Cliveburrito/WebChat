package com.example.WebChat.conversation;

import com.example.WebChat.conversation.ConvMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationMembersCacheService {
    private static final String KEY_PREFIX = "conversation:members:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ConvMembershipRepository convMembershipRepository;

    public List<Long> getMemberIds(Long conversationId) {
        String key = KEY_PREFIX + conversationId;
        List<String> cached = redisTemplate.opsForList().range(key, 0, -1);
        if (cached != null && !cached.isEmpty()) {
            return cached.stream().map(Long::valueOf).toList();
        }

        List<Long> memberIds = convMembershipRepository.getAllMemberIds(conversationId);
        if (!memberIds.isEmpty()) {
            redisTemplate.delete(key);
            redisTemplate.opsForList().rightPushAll(key, memberIds.stream().map(String::valueOf).toList());
            redisTemplate.expire(key, TTL);
        }
        return memberIds;
    }

    public void evict(Long conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
    }
}
