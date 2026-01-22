package com.example.WebChat.Service;

import com.example.WebChat.DTO.ConversationResponse;
import com.example.WebChat.DTO.OpenGroupChatRequest;
import com.example.WebChat.Entity.ConvMembership;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Entity.Message;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Repository.ConversationRepository;
import com.example.WebChat.Repository.ConvMembershipRepository;
import com.example.WebChat.Repository.MessageRepository;
import com.example.WebChat.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
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
    private final MessageRepository messageRepository;

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
                    // return the Id at once
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
        convMembershipRepository.findByUser_UsernameAndConversation_ConversationID(
                username, conversationId).ifPresent(membership -> {
                    if (membership.getUnreadCount() > 0) {
                        membership.setUnreadCount(0);
                        convMembershipRepository.save(membership);
                        log.info("Marked conversation {} as read for user {}", conversationId, username);
                    }
                });
    }

    /**
     * Creates a group conversation with a list of users.
     */
    @Transactional
    public Conversation createGroupChat(OpenGroupChatRequest request, String creatorUsername) {
        // Create the Conversation entity
        Conversation conv = new Conversation();
        conv.setConversationName(request.groupName());
        conv.setGroup(true);
        conv.setCreatedAt(Instant.now());
        conv = conversationRepository.save(conv);

        // Get all member users + the creator
        List<Long> allIds = new ArrayList<>(request.memberIds());
        User creator = userRepository.findByUsername(creatorUsername).orElseThrow();
        if (!allIds.contains(creator.getId())) {
            allIds.add(creator.getId());
        }

        // Create memberships for everyone
        for (Long userId : allIds) {
            User user = userRepository.findById(userId).orElseThrow();
            ConvMembership membership = new ConvMembership();
            membership.setUser(user);
            membership.setConversation(conv);
            membership.setJoinedAt(Instant.now());
            membership.setUnreadCount(0);
            convMembershipRepository.save(membership);
        }
        return conv;
    }

    /**
     * Retrieves all conversations for a specific user with formatted display names and last messages.
     */
    public List<ConversationResponse> getUserChats(String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<ConvMembership> memberships = convMembershipRepository.findAllByUser_Id(currentUser.getId());
        List<ConversationResponse> responseList = new ArrayList<>();

        for (ConvMembership m : memberships) {
            Conversation conv = m.getConversation();

            String displayName = conv.getConversationName();
            if (!conv.isGroup()) {
                displayName = convMembershipRepository.findAllByConversation_ConversationID(conv.getConversationID())
                        .stream()
                        .map(membership -> membership.getUser().getUsername())
                        .filter(name -> !name.equals(username))
                        .findFirst()
                        .orElse("Direct Chat");
            }

            Optional<Message> lastMsg = messageRepository.findLastMessage(conv.getConversationID());

            String lastContent = lastMsg.map(Message::getMessage).orElse("No messages yet");
            Instant sentAt = lastMsg.map(Message::getSentAt).orElse(null);

            responseList.add(new ConversationResponse(
                    conv.getConversationID(),
                    displayName,
                    "default-avatar.png",
                    lastContent,
                    m.getUnreadCount(),
                    sentAt // might be null if no msgs
            ));
        }

        // Sorting to have the latest message first
        responseList.sort((a, b) -> {
            if (a.lastMessageAt() == null && b.lastMessageAt() == null) return 0;
            if (a.lastMessageAt() == null) return 1;
            if (b.lastMessageAt() == null) return -1;
            return b.lastMessageAt().compareTo(a.lastMessageAt());
        });

        return responseList;
    }

    /**
     * Fetches paginated messages with membership verification.
     */
    public Page<Message> getMessagesByConversationId(Long conversationId, String currentUsername, int page, int size) {
        boolean isMember = convMembershipRepository.existsByUser_UsernameAndConversation_ConversationID(currentUsername, conversationId);
        if (!isMember) {
            throw new AccessDeniedException("You are not a member of this conversation.");
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by("sentAt").descending());
        return messageRepository.findByConversation_ConversationID(conversationId, pageable);
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
}