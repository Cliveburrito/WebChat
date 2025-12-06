package com.example.WebChat.Service;

import com.example.WebChat.Entity.ConvMembership;
import com.example.WebChat.Entity.Conversation;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Repository.ConversationRepository;
import com.example.WebChat.Repository.ConvMembershipRepository;
import com.example.WebChat.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j                                       // Enables log.info, log.error, etc.
@Service                                     // Marks this class as a Spring service (business logic)
@RequiredArgsConstructor                     // Generates constructor for all final fields (for DI)
public class ConversationService {

    // These are injected by Spring via the generated constructor (because they are final).
    private final ConversationRepository conversationRepository;
    private final ConvMembershipRepository convMembershipRepository;
    private final UserRepository userRepository;

    /**
     * Creates a 1–1 (direct) conversation between exactly two distinct users.
     * Business rules for direct chats:
     *  - both users must be non-null
     *  - users must be different (no chatting with yourself)
     *  - exactly 2 participants
     */
    public Conversation createDirectConversation(Long user1ID, Long user2ID) {
        // 1. Fetch and Validate in one step
        User user1 = userRepository.findById(user1ID)
                .orElseThrow(() -> new IllegalArgumentException("User with ID " + user1ID + " not found"));

        User user2 = userRepository.findById(user2ID)
                .orElseThrow(() -> new IllegalArgumentException("User with ID " + user2ID + " not found"));

        // Put the two users into a list to pass to the shared internal method
        List<User> users = List.of(user1, user2);

        // For direct chats we:
        //  - set isGroup = false
        //  - often don't need a name (can be null for now)
        boolean isGroup = false;

        // Delegate to the shared internal method that actually creates the Conversation + memberships
        Conversation conversation = createConversationFunction(users, isGroup, user1.getUsername() + " " + user2.getUsername()
                // Delegate to the shared internal method that actually creates the Conversation + memberships
        );

        log.info("Created direct conversation {} between users {} and {}",
                conversation.getConversationName(), user1.getId(), user2.getId());

        return conversation;
    }

    /**
     * Creates a group conversation with the given users and name.
     * Business rules for group chats (you can tweak these):
     *  - at least 3 users (or 2 if you want)
     *  - name must not be null or blank
     *  - no duplicate users
     */
    public Conversation createGroupConversation(List<Long> userIDs, String name) {
        List<User> users = userRepository.findAllById(userIDs);
        // Null/empty checks to ensure we have participants
        if (users.isEmpty()) {
            throw new IllegalArgumentException("Group conversation requires at least one user in the list.");
        }

        // Validate the name: a group should usually have a visible name
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Group conversation must have a non-empty name.");
        }

        boolean isGroup = true;

        // Delegate to the internal method that handles the actual creation logic
        Conversation conversation = createConversationFunction(users, isGroup, name);

        log.info("Created group conversation {} with name '{}' and {} members",
                conversation.getConversationID(), name, users.size());

        return conversation;
    }

    /**
     * Shared internal method that:
     *  - creates a Conversation entity
     *  - saves it to the database
     *  - creates ConvMembership rows for each user
     * <p>
     * This method is private because we want the public API to clearly express
     * whether we are creating a direct or group conversation.
     */

    private Conversation createConversationFunction(List<User> users, boolean isGroup, String name) {
        // Safety check: we assume the caller (public methods) already validated,
        // but we can still protect against totally invalid input here.
        if (users == null || users.size() < 2) {
            throw new IllegalArgumentException("Conversation must have at least two user.");
        }

        // Use the current time once so that createdAt and joinedAt can share the same base moment if desired
        Instant now = Instant.now();

        // 1. Build the Conversation entity using Lombok's builder.
        Conversation conversation = Conversation.builder()
                .createdAt(now)         // when the conversation was created
                .conversationName(name) // can be null for direct, non-null for group
                .isGroup(isGroup)       // true for groups, false for direct chats
                .build();

        // 2. Save the Conversation to the database.
        //    After saving, 'conversation' will have its generated ID populated.
        conversation = conversationRepository.save(conversation);

        // 3. For each user, create a ConvMembership row linking that user to this conversation.
        List<ConvMembership> memberships = new ArrayList<>();

        for (User user : users) {
            if (user == null) {
                // If somehow a null user slipped through, we skip or throw an error.
                // Here, we choose to fail fast.
                throw new IllegalArgumentException("Null user in users list is not allowed.");
            }

            // Create a new membership for this (user, conversation) pair.
            ConvMembership membership = ConvMembership.builder()
                    .conversation(conversation)  // link to the conversation we just created
                    .user(user)                 // the user participating in the conversation
                    .joinedAt(now)              // when the user joined (we reuse 'now' for consistency)
                    .muted(false)               // default: not muted
                    .notificationsOn(true)      // default: notifications are enabled
                    .build();

            memberships.add(membership);
        }

        // 4. Save all memberships in one go (more efficient than saving one by one).
        convMembershipRepository.saveAll(memberships);

        // 5. Return the fully created conversation.
        //    At this point, the DB has:
        //      - 1 row in 'conversations'
        //      - N rows in 'conversation_membership'
        return conversation;
    }
}
