package com.example.WebChat;

import com.example.WebChat.DTO.ChatMessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class RedisTests {

    @Autowired StringRedisTemplate redisTemplate;
    @Autowired ObjectMapper objectMapper;

    private final String unreadKey = "chat:unread:42";
    private final String historyKey = "chat:history:999";

    @BeforeEach
    void clean() {
        redisTemplate.delete(unreadKey);
        redisTemplate.delete(historyKey);
    }

    @Test
    void shouldIncrementUnreadCount() {
        redisTemplate.opsForHash().increment(unreadKey, "1001", 1);
        redisTemplate.opsForHash().increment(unreadKey, "1001", 1);
        redisTemplate.opsForHash().increment(unreadKey, "1001", 1);

        Object unread = redisTemplate.opsForHash().get(unreadKey, "1001");
        assertEquals("3", String.valueOf(unread));
    }

    @Test
    void shouldResetUnreadCount() {
        redisTemplate.opsForHash().increment(unreadKey, "1001", 5);
        redisTemplate.opsForHash().put(unreadKey, "1001", "0");

        Object unread = redisTemplate.opsForHash().get(unreadKey, "1001");
        assertEquals("0", String.valueOf(unread));
    }

    @Test
    void shouldStoreAndTrimTo100Messages() throws Exception {
        for (int i = 1; i <= 120; i++) {
            ChatMessageResponse msg = new ChatMessageResponse(
                    (long) i, "Message " + i, Instant.now(), "user", 999L, null
            );
            String json = objectMapper.writeValueAsString(msg);

            redisTemplate.opsForList().leftPush(historyKey, json);
            redisTemplate.opsForList().trim(historyKey, 0, 99);
        }

        Long size = redisTemplate.opsForList().size(historyKey);
        assertEquals(100L, size);

        String newestJson = redisTemplate.opsForList().index(historyKey, 0);
        ChatMessageResponse newest = objectMapper.readValue(newestJson, ChatMessageResponse.class);

        assertEquals(120L, newest.id());
    }
}
