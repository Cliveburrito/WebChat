package com.example.WebChat.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@Configuration
public class RedisConfig {
    @Bean
    @Primary
    public ThreadSafeFory fury() {
        // 1. Initialize the engine with "Safe Mode" settings
        ThreadSafeFory fory = Fory.builder()
                .withLanguage(Language.JAVA)
                // Keep FALSE to handle internal Java types like List.of()
                .requireClassRegistration(true)
                // DISABLE this to prevent the "read objects are: [null]" error
                .withRefTracking(false)
                .withCodegen(false)
                        .buildThreadSafeForyPool(10, 100);


        // 2. REGISTER THE "DNA" (Fixed IDs for your Records)
        // We register them in a specific order to ensure every thread has the same map

        fory.register(com.example.WebChat.attachment.dto.AttachmentDTO.class, 258);
        fory.register(com.example.WebChat.message.dto.ChatMessageResponse.class, 259);
        fory.register(com.example.WebChat.message.dto.MessageReactionSummary.class, 260);



        log.info("Surgical initialization of the Singleton Fury Instance (RefTracking: OFF)");
        return fory;
    }

    @Bean(name = "furyRedisTemplate")
    public RedisTemplate<String, byte[]> furyRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Keys stay as Strings so we can read them in the CLI
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Values stay as raw bytes (Fury handles the rest)
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashValueSerializer(RedisSerializer.byteArray());

        return template;
    }

    @Bean
    public RedisScript<Long> atomicTrimScript() {
        String script =
                "local max = tonumber(ARGV[1]) " +
                        "local size = redis.call('ZCARD', KEYS[1]) " +
                        "if size <= max then return 0 end " +
                        "local extra = size - max " +
                        "local expiredIds = redis.call('ZRANGE', KEYS[1], 0, extra - 1) " +
                        "if #expiredIds > 0 then " +
                        "  redis.call('ZREM', KEYS[1], unpack(expiredIds)) " +
                        "  redis.call('HDEL', KEYS[2], unpack(expiredIds)) " +
                        "end " +
                        "return #expiredIds";
        return RedisScript.of(script, Long.class);
    }
}
