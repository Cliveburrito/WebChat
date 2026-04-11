package com.example.WebChat.conversation;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class ConversationCacheService {
    public static final String USER_CHAT_LISTS_CACHE = "user_chat_lists";

    private final CacheManager cacheManager;

    public void evictUserChats(Long userId) {
        Cache cache = cacheManager.getCache(USER_CHAT_LISTS_CACHE);
        if (cache == null || userId == null) {
            return;
        }
        cache.evict(userId);
    }

    public void evictUserChats(Collection<Long> userIds) {
        if (userIds == null) {
            return;
        }
        userIds.forEach(this::evictUserChats);
    }
}
