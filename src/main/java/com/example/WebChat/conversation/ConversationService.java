package com.example.WebChat.conversation;


import com.example.WebChat.conversation.dto.ChatListRow;
import com.example.WebChat.conversation.dto.ConversationResponse;
import com.example.WebChat.conversation.dto.ConversationWatermarkResponse;
import com.example.WebChat.conversation.dto.ConversationWatermarkRow;
import com.example.WebChat.conversation.dto.GroupChatRequest;
import com.example.WebChat.user.dto.UserResponse;
import com.example.WebChat.user.User;
import com.example.WebChat.shared.ResourceNotFoundException;
import com.example.WebChat.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {
    private final StringRedisTemplate redisTemplate;
    private final ConversationRepository conversationRepository;
    private final ConvMembershipRepository convMembershipRepository;
    private final UserRepository userRepository;
    private final MembershipGuard membershipGuard;
    private final ConversationMembersCacheService conversationMembersCacheService;

    /**
     * Creates or retrieves a 1-1 direct conversation between two users.
     * Checks if a direct chat already exists to prevent duplicates.
     */
    @Transactional
    public Long createDirectConversation(Long user1ID, Long user2ID) {
        if (user1ID.equals(user2ID)) {
            throw new IllegalArgumentException("You cannot start a conversation with yourself.");
        }

        Optional<Long> existingId = convMembershipRepository.findExistingDirectChatId(user1ID, user2ID);

        if (existingId.isPresent()) {
            log.info("Found existing direct conversation (ID: {})", existingId.get());
            return existingId.get();
        }

        User user1 = userRepository.findById(user1ID).
                orElseThrow(() -> new ResourceNotFoundException("User not found: " + user1ID));
        User user2 = userRepository.findById(user2ID).
                orElseThrow(() -> new ResourceNotFoundException("User not found: " + user2ID));

        Conversation newConv = createConversationFunction(List.of(user1, user2), false, null);
        log.info("Created a new direct conversation (ID: {})", newConv.getId());
        return newConv.getId();
    }

    @Transactional
    @CacheEvict(value = ConversationCacheService.USER_CHAT_LISTS_CACHE, key = "#user1Id")
    public ConversationResponse openDirectChatPreview(Long user1Id, Long user2Id) {
        Long conversationId = createDirectConversation(user1Id, user2Id);

        User user2 = userRepository.findById(user2Id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + user2Id));

        String displayName = user2.getUsername();

        return new ConversationResponse(
                conversationId,
                displayName,
                "default-avatar.png",
                "",
                0L,
                null,
                null,
                null,
                false,
                false,
                0L,
                0L,
                List.of()
        );
    }


    /**
     * Creates a group conversation with a list of users
     */
    @Transactional
    public Conversation createGroupChat(GroupChatRequest request, Long id) {
        User creator = userRepository.getReferenceById(id);

        Set<Long> uniqueIds = new LinkedHashSet<>(request.memberIds());
        uniqueIds.add(creator.getId());

        List<User> users = userRepository.findAllById(uniqueIds);

        if (users.size() != uniqueIds.size()) {
            throw new ResourceNotFoundException("One or more requested users do not exist.");
        }

        if (users.size() < 2) {
            throw new IllegalArgumentException("A group chat must include at least two members.");
        }

        return createConversationFunction(users, true, request.groupName());
    }

    @Transactional
    @CacheEvict(value = ConversationCacheService.USER_CHAT_LISTS_CACHE, key = "#id")
    public ConversationResponse createGroupChatPreview(GroupChatRequest request, Long id) {
        Conversation conv = createGroupChat(request, id);

        return new ConversationResponse(
                conv.getId(),
                conv.getConversationName(),
                "default-avatar.png",
                "",
                0L,
                null,
                null,
                null,
                true,
                false,
                0L,
                0L,
                List.of()

        );
    }

    @Cacheable(value = ConversationCacheService.USER_CHAT_LISTS_CACHE, key = "#id")
    public List<ConversationResponse> getUserChats(Long id) {
        // 1. Fetch from DB (The Beast Query)
        List<ChatListRow> rows = convMembershipRepository.findUserChats(id);
        Map<Long, List<ConversationWatermarkResponse>> persistedWatermarks = convMembershipRepository
                .findOtherParticipantWatermarks(id)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ConversationWatermarkRow::getConversationId,
                        java.util.stream.Collectors.mapping(
                                row -> new ConversationWatermarkResponse(
                                        row.getUserId(),
                                        row.getLastDeliveredMessageId(),
                                        row.getLastReadMessageId()
                                ),
                                java.util.stream.Collectors.toList()
                        )
                ));

        return rows.stream()
                .map(r -> toConversationResponse(id, r, persistedWatermarks.getOrDefault(r.getConversationId(), List.of())))
                .toList();
    }

    public ConversationResponse getConversationPreviewForUser(Long userId, Long conversationId) {
        ChatListRow row = convMembershipRepository.findUserChat(userId, conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));

        List<ConversationWatermarkResponse> watermarks = convMembershipRepository
                .findOtherParticipantWatermarks(userId)
                .stream()
                .filter(w -> Objects.equals(w.getConversationId(), conversationId))
                .map(w -> new ConversationWatermarkResponse(
                        w.getUserId(),
                        w.getLastDeliveredMessageId(),
                        w.getLastReadMessageId()
                ))
                .toList();

        return toConversationResponse(userId, row, watermarks);
    }

    private ConversationResponse toConversationResponse(Long userId, ChatListRow r, List<ConversationWatermarkResponse> participantWatermarks) {
        String metaKey = "conv:meta:" + r.getConversationId();
        Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey);

        String content = meta.containsKey("lastContent")
                ? (String) meta.get("lastContent")
                : (r.getLastContent() == null ? "" : r.getLastContent());

        Instant lastAt = r.getLastMessageAt();
        if (meta.containsKey("lastMessageAt")) {
            try {
                lastAt = Instant.parse((String) meta.get("lastMessageAt"));
            } catch (Exception e) { /* ignore */ }
        }

        Long msgId = r.getLastMessageId();
        if (meta.containsKey("lastMessageId")) {
            try {
                msgId = Long.parseLong((String) meta.get("lastMessageId"));
            } catch (Exception e) { /* ignore */ }
        }

        Long senderId = r.getLastSenderId();
        if (meta.containsKey("lastSenderId")) {
            try {
                senderId = Long.parseLong((String) meta.get("lastSenderId"));
            } catch (Exception e) { /* ignore */ }
        }

        String selfWatermarkKey = "watermarks:" + r.getConversationId() + ":" + userId;
        Long redisDelivered = parseLong((String) redisTemplate.opsForHash().get(selfWatermarkKey, "delivered"));
        Long redisRead = parseLong((String) redisTemplate.opsForHash().get(selfWatermarkKey, "read"));
        Long myDelivered = max(r.getMyLastDeliveredMessageId(), redisDelivered);
        Long myRead = max(r.getMyLastReadMessageId(), redisRead);
        Long unread = r.getUnreadCount() == null ? 0L : r.getUnreadCount();
        if (msgId != null && !Objects.equals(senderId, userId) && myRead >= msgId) {
            unread = 0L;
        }

        return new ConversationResponse(
                r.getConversationId(),
                r.getDisplayName(),
                "default-avatar.png",
                content,
                unread,
                lastAt,
                msgId,
                senderId,
                r.getIsGroup(),
                r.getMuted(),
                myDelivered,
                myRead,
                participantWatermarks
        );
    }

    private static Long max(Long left, Long right) {
        return Math.max(left == null ? 0L : left, right == null ? 0L : right);
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Internal helper to persist a new Conversation and its Memberships.
     */
    private Conversation createConversationFunction(List<User> users, boolean isGroup, String name) {
        Instant now = Instant.now();
        Conversation conversation = Conversation.builder()
                .createdAt(now)
                .conversationName(name)
                .isGroup(isGroup)
                .build();

        conversation = conversationRepository.save(conversation);

        List<ConvMembership> memberships = new ArrayList<>();
        for (User user : users) {
            memberships.add(ConvMembership.builder()
                    .conversation(conversation)
                    .user(user)
                    .joinedAt(now)
                    .muted(false)
                    .notificationsOn(true)
                    .build());
        }
        convMembershipRepository.saveAll(memberships);
        conversationMembersCacheService.evict(conversation.getId());

        return conversation;
    }

    @Transactional
    @CacheEvict(value = ConversationCacheService.USER_CHAT_LISTS_CACHE, key = "#id")
    public void toggleMute(Long id, Long conversationId, boolean status) {
        if (!membershipGuard.isMember(id, conversationId)) {
            throw new AccessDeniedException("You are not a member of this conversation.");
        }

        convMembershipRepository.toggleMute(id, conversationId, status);
    }

    public List<UserResponse> getConversationMembers(Long conversationId, Long userId) {
        if(!membershipGuard.isMember(userId, conversationId)) {
            throw new AccessDeniedException("You are not a mamber of this chat!");
        }

        return convMembershipRepository.getAllMembers(conversationId).stream()
                .map(UserResponse::fromEntity)
                .toList();
    }
}
