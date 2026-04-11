package com.example.WebChat.message;

import com.example.WebChat.conversation.MembershipGuard;
import com.example.WebChat.message.dto.ChatMessageResponse;
import com.example.WebChat.message.dto.MessageReactionSummary;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fory.ThreadSafeFory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageQueryService {
    private final MessageRepository messageRepository;
    private final MembershipGuard membershipGuard;
    private final MessageCacheService messageCacheService;
    private final StringRedisTemplate stringRedisTemplate;
    @Qualifier("furyRedisTemplate")
    private final RedisTemplate<String, byte[]> furyRedisTemplate;
    private final ThreadSafeFory fury;
    private final MessageReactionRepository messageReactionRepository;

    public boolean canAccessConversation(Long userId, Long conversationId) {
        return membershipGuard.isMember(userId, conversationId);
    }

    @Transactional
    public List<ChatMessageResponse> getChatHistory(Long conversationId, int page, int size, Long userId) {
        if (!membershipGuard.isMember(userId, conversationId)) {
            throw new AccessDeniedException("Not a member.");
        }

        String indexKey = messageCacheService.getIndexKey(conversationId);
        String dataKey = messageCacheService.getDataKey(conversationId);

        int start = page * size;
        int end = start + size - 1;

        if (end < 100) {
            List<ChatMessageResponse> cachedResponses = getCachedHistory(indexKey, dataKey, start, end, page, conversationId);
            if (!cachedResponses.isEmpty()) {
                return enrichReactions(cachedResponses, userId);
            }
        }

        if (page == 0) {
            log.info("Postgres cold start -> warm up Redis (100) conv={}", conversationId);

            Pageable warmUpPageable = PageRequest.of(0, 100, Sort.by("sentAt").descending());
            Slice<Long> allIds = messageRepository.findMessageIds(conversationId, warmUpPageable);
            if (allIds.isEmpty()) {
                return List.of();
            }

            List<ChatMessageResponse> allResponses = messageRepository.findMessagesWithDetails(allIds.getContent())
                    .stream()
                    .map(ChatMessageResponse::fromEntity)
                    .toList();

            messageCacheService.refreshRedisCache(conversationId, allResponses);
            return enrichReactions(allResponses.stream().limit(size).toList(), userId);
        }

        log.info("Postgres read page={} conv={}", page, conversationId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("sentAt").descending());
        List<Long> ids = messageRepository.findMessageIds(conversationId, pageable).getContent();
        if (ids.isEmpty()) {
            return List.of();
        }

        List<ChatMessageResponse> responses = messageRepository.findMessagesWithDetails(ids).stream()
                .map(ChatMessageResponse::fromEntity)
                .toList();
        return enrichReactions(responses, userId);
    }

    public List<ChatMessageResponse> searchInChat(Long userId, Long conversationId, String query) {
        if (!membershipGuard.isMember(userId, conversationId)) {
            throw new AccessDeniedException("You are not a member of this conversation!");
        }

        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return List.of();
        }

        List<Long> messageIds = messageRepository.searchMessageIds(conversationId, normalizedQuery);
        if (messageIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Message> messagesById = messageRepository.findMessagesWithDetails(messageIds)
                .stream()
                .collect(Collectors.toMap(Message::getId, message -> message));

        List<ChatMessageResponse> results = messageIds.stream()
                .map(messagesById::get)
                .filter(Objects::nonNull)
                .map(ChatMessageResponse::fromEntity)
                .toList();
        return enrichReactions(results, userId);
    }

    private List<ChatMessageResponse> enrichReactions(List<ChatMessageResponse> messages, Long userId) {
        if (messages.isEmpty()) {
            return messages;
        }

        List<Long> messageIds = messages.stream()
                .map(ChatMessageResponse::id)
                .filter(Objects::nonNull)
                .toList();

        if (messageIds.isEmpty()) {
            return messages;
        }

        Map<Long, List<MessageReactionSummary>> summariesByMessageId = messageReactionRepository
                .summarizeForMessages(messageIds, userId)
                .stream()
                .collect(Collectors.groupingBy(
                        row -> row.messageId(),
                        Collectors.mapping(
                                row -> new MessageReactionSummary(row.emoji(), row.count(), row.reactedByMe()),
                                Collectors.toList()
                        )
                ));

        return messages.stream()
                .map(message -> new ChatMessageResponse(
                        message.id(),
                        message.content(),
                        message.createdAt(),
                        message.senderUsername(),
                        message.conversationId(),
                        message.replyToMessageId(),
                        message.replyToSenderUsername(),
                        message.replyToContent(),
                        summariesByMessageId.getOrDefault(message.id(), List.of()),
                        message.attachments()
                ))
                .toList();
    }

    private List<ChatMessageResponse> getCachedHistory(String indexKey, String dataKey, int start, int end, int page, Long conversationId) {
        var messageIds = stringRedisTemplate.opsForZSet().reverseRange(indexKey, start, end);
        if (messageIds == null || messageIds.isEmpty()) {
            return List.of();
        }

        List<Object> raw = furyRedisTemplate.opsForHash().multiGet(dataKey, new ArrayList<>(messageIds));
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        log.info("Redis cache hit (Fory bytes) page={} conv={}", page, conversationId);
        List<ChatMessageResponse> responses = new ArrayList<>(raw.size());
        for (Object entry : raw) {
            if (entry instanceof byte[] bytes) {
                try {
                    responses.add((ChatMessageResponse) fury.deserialize(bytes));
                } catch (Exception e) {
                    log.error("Fury failed to read a message. Skipping to keep UI alive. Error: {}", e.getMessage());
                }
            }
        }
        return responses;
    }
}
