package com.example.WebChat.Service;

import com.example.WebChat.DTO.ChatListRow;
import com.example.WebChat.DTO.ConversationResponse;
import com.example.WebChat.DTO.OpenGroupChatRequest;
import com.example.WebChat.Entity.ConvMembership;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Exception.ResourceNotFoundException;
import com.example.WebChat.Repository.ConversationRepository;
import com.example.WebChat.Repository.ConvMembershipRepository;
import com.example.WebChat.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
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
                orElseThrow(() -> new RuntimeException("User 1 not found"));
        User user2 = userRepository.findById(user2ID).
                orElseThrow(() -> new RuntimeException("User 2 not found"));

        Conversation newConv = createConversationFunction(List.of(user1, user2), false, null);
        log.info("Created a new direct conversation (ID: {})", newConv.getConversationID());
        return newConv.getConversationID();
    }

    @Transactional
    public ConversationResponse openDirectChatPreview(Long user1Id, Long user2Id) {
        Long conversationId = createDirectConversation(user1Id, user2Id);

        // Sidebar name for direct chat: show "the other user's username"
        User user1 = userRepository.findById(user1Id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User user2 = userRepository.findById(user2Id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String displayName = user2.getUsername(); // if user1 is "me", show user2

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
                false
        );
    }


    /**
     * Creates a group conversation with a list of users
     */
    @Transactional
    public Conversation createGroupChat(OpenGroupChatRequest request, Long id) {
        User creator = userRepository.getReferenceById(id);

        List<Long> allIds = new ArrayList<>(request.memberIds());
        if (!allIds.contains(creator.getId())) {
            allIds.add(creator.getId());
        }

        List<User> users = userRepository.findAllById(allIds);

        return createConversationFunction(users, true, request.groupName());
    }

    @Transactional
    public ConversationResponse createGroupChatPreview(OpenGroupChatRequest request, Long id) {
        Conversation conv = createGroupChat(request, id);

        return new ConversationResponse(
                conv.getConversationID(),
                conv.getConversationName(),
                "default-avatar.png",
                "",
                0L,
                null,
                null,
                null,
                true,
                false

        );
    }

    public List<ConversationResponse> getUserChats(Long id) {
        // 1. Fetch from DB (The Beast Query)
        List<ChatListRow> rows = convMembershipRepository.findUserChats(id);

        return rows.stream().map(r -> {
            // 2. Check Redis for 'Hot' updates
            // Αν μόλις έστειλες μήνυμα και δεν έχει προλάβει να γραφτεί στη βάση,
            // η Redis θα έχει τα πιο φρέσκα δεδομένα.
            String metaKey = "conv:meta:" + r.getConversationId();
            Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey);

            // --- A. CONTENT ---
            String content = meta.containsKey("lastContent")
                    ? (String) meta.get("lastContent")
                    : (r.getLastContent() == null ? "" : r.getLastContent());

            // --- B. TIMESTAMP ---
            Instant lastAt = r.getLastMessageAt();
            if (meta.containsKey("lastMessageAt")) {
                try {
                    lastAt = Instant.parse((String) meta.get("lastMessageAt"));
                } catch (Exception e) { /* ignore */ }
            }

            // --- C. MESSAGE ID (Για τα Ticks) ---
            Long msgId = r.getLastMessageId();
            if (meta.containsKey("lastMessageId")) {
                try {
                    msgId = Long.parseLong((String) meta.get("lastMessageId"));
                } catch (Exception e) { /* ignore */ }
            }

            // --- D. SENDER ID (Για να ξέρουμε αν βάζουμε "You:") ---
            Long senderId = r.getLastSenderId();
            if (meta.containsKey("lastSenderId")) {
                try {
                    senderId = Long.parseLong((String) meta.get("lastSenderId"));
                } catch (Exception e) { /* ignore */ }
            }

            // --- E. Unread Count (Παραμένει από τη SQL για τώρα) ---
            // (Το Redis unread count είναι δύσκολο σπορ, ας εμπιστευτούμε τη βάση)
            Long unread = r.getUnreadCount() == null ? 0L : r.getUnreadCount();

            return new ConversationResponse(
                    r.getConversationId(),
                    r.getDisplayName(),
                    "default-avatar.png", // Ή r.getAvatarUrl() αν το έχεις
                    content,
                    unread,
                    lastAt,
                    msgId,     // <--- New
                    senderId,  // <--- New
                    r.getIsGroup(), // <--- Αν το πρόσθεσες στο SQL
                    r.getMuted()
            );
        }).toList();
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

        return conversation;
    }

    @Transactional
    public void toggleMute(Long id, Long conversationId, boolean status) {

        convMembershipRepository.toggleMute(id, conversationId, status);
    }
}