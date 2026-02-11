package com.example.WebChat.Service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

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

        // Check for existing direct conversation
        List<ConvMembership> memberships = convMembershipRepository.findAllByUser_Id(user1ID);
        for (ConvMembership m : memberships) {
            Conversation conv = m.getConversation();
            if (!conv.isGroup()) {
                boolean isOtherPresent = convMembershipRepository.existsByUser_IdAndConversation_ConversationID(user2ID, conv.getConversationID());
                if (isOtherPresent) {
                    log.info("Found existing direct conversation (ID: {})", conv.getConversationID());
                    // return the ID at once
                    return conv.getConversationID();
                }
            }
        }

        User user1 = userRepository.findById(user1ID).
                orElseThrow(() -> new RuntimeException("User 1 not found"));
        User user2 = userRepository.findById(user2ID).
                orElseThrow(() -> new RuntimeException("User 2 not found"));

        Conversation newConv = createConversationFunction(List.of(user1, user2), false, null);
        log.info("Created a new direct conversation (ID: {})", newConv.getConversationID());
        return newConv.getConversationID();
    }

    /**
     *  Find the member user for this chat
     *  and set his unreadMsg count to 0
     */
    @Transactional
    public void markAsRead(Long conversationId, String username) {
        convMembershipRepository.resetUnreadCount(conversationId, username);
        log.info("Marked conversation {} as read for user {}", conversationId, username);
    }

    /**
     * Creates a group conversation with a list of users
     */
    @Transactional
    public Conversation createGroupChat(OpenGroupChatRequest request, String creatorUsername) {
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found"));

        List<Long> allIds = new ArrayList<>(request.memberIds());
        if (!allIds.contains(creator.getId())) {
            allIds.add(creator.getId());
        }

        List<User> users = userRepository.findAllById(allIds);

        return createConversationFunction(users, true, request.groupName());
    }

    public List<ConversationResponse> getUserChats(String username) {
        User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return convMembershipRepository.findUserChats(u.getId())
                .stream()
                .map(r -> new ConversationResponse(
                        r.getConversationId(),
                        r.getDisplayName(),
                        "default-avatar.png",
                        r.getLastContent(),
                        r.getUnreadCount(),
                        r.getLastMessageAt()
                ))
                .toList();
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
    public void toggleMute(String username, Long conversationId, boolean status) {
        // Βρίσκουμε το membership του συγκεκριμένου χρήστη για το συγκεκριμένο chat
        convMembershipRepository.toggleMute(username, conversationId, status);
    }
}