package com.example.WebChat.presence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.WebChat.user.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    private static final String REDIS_SET_KEY = "chat:online_users";
    private static final String HEARTBEAT_PREFIX = "user:heartbeat:";

    // Called when the socket connects
    public void onConnect(String username) {
        redisTemplate.opsForSet().add(REDIS_SET_KEY, username);
        updateHeartbeat(username); // Initialize heartbeat
        broadcastPresence();
    }

    // Called when the socket disconnects gracefully
    public void onDisconnect(String username) {
        redisTemplate.opsForSet().remove(REDIS_SET_KEY, username);
        redisTemplate.delete(HEARTBEAT_PREFIX + username);
        userRepository.touchLastSeenAt(username);
        broadcastPresence();
    }

    public void updateHeartbeat(String username) {
        redisTemplate.opsForValue().set(HEARTBEAT_PREFIX + username, "active", Duration.ofMinutes(2));
    }


    public void broadcastPresence() {
        Set<String> onlineUsers = redisTemplate.opsForSet().members(REDIS_SET_KEY);
        if (onlineUsers == null) onlineUsers = Set.of();
        messagingTemplate.convertAndSend("/topic/public/presence", onlineUsers);

    }

    public Set<String> getOnlineUsers() {
        return redisTemplate.opsForSet().members(REDIS_SET_KEY);
    }

    @Scheduled(fixedRate = 60000)
    public void performCleanup() {
        Set<String> onlineUsers = redisTemplate.opsForSet().members(REDIS_SET_KEY);
        if (onlineUsers == null) return;

        boolean changed = false;
        for (String username : onlineUsers) {
            if (!redisTemplate.hasKey(HEARTBEAT_PREFIX + username)) {
                redisTemplate.opsForSet().remove(REDIS_SET_KEY, username);
                userRepository.touchLastSeenAt(username);
                log.info("User {} timed out and was marked offline by Scheduler", username);
                changed = true;
            }
        }

        if (changed) {
            broadcastPresence();
        }
    }
}
