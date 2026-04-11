package com.example.WebChat.message;

import com.example.WebChat.conversation.MembershipGuard;
import com.example.WebChat.message.dto.MessageReactionEvent;
import com.example.WebChat.user.User;
import com.example.WebChat.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MessageReactionService {
    private static final Set<String> ALLOWED_EMOJIS = Set.of("👍", "❤️", "😂", "😮", "😢", "🔥");

    private final MessageRepository messageRepository;
    private final MessageReactionRepository reactionRepository;
    private final UserRepository userRepository;
    private final MembershipGuard membershipGuard;
    private final MessageCacheService messageCacheService;

    @Transactional
    public List<MessageReactionEvent> toggleReaction(Long userId, Long messageId, String emoji) {
        String normalizedEmoji = normalizeEmoji(emoji);
        Message message = messageRepository.findByIdWithSenderAndConversation(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        Long conversationId = message.getConversation().getId();
        if (!membershipGuard.isMember(userId, conversationId)) {
            throw new AccessDeniedException("You are not a member of this conversation.");
        }

        List<MessageReactionEvent> events = new ArrayList<>();
        List<MessageReaction> existingReactions = reactionRepository.findByMessageIdAndUserId(messageId, userId);
        boolean sameReactionAlreadySet = existingReactions.stream()
                .anyMatch(reaction -> normalizedEmoji.equals(reaction.getEmoji()));

        if (!existingReactions.isEmpty()) {
            Set<String> removedEmojis = existingReactions.stream()
                    .map(MessageReaction::getEmoji)
                    .collect(java.util.stream.Collectors.toSet());
            reactionRepository.deleteAll(existingReactions);
            reactionRepository.flush();
            removedEmojis.forEach(removedEmoji -> events.add(new MessageReactionEvent(
                    messageId,
                    conversationId,
                    userId,
                    removedEmoji,
                    reactionRepository.countByMessageIdAndEmoji(messageId, removedEmoji),
                    "removed"
            )));
        }

        if (!sameReactionAlreadySet) {
            User user = userRepository.getReferenceById(userId);
            reactionRepository.save(MessageReaction.builder()
                    .message(message)
                    .user(user)
                    .emoji(normalizedEmoji)
                    .createdAt(Instant.now())
                    .build());
            reactionRepository.flush();
            events.add(new MessageReactionEvent(
                    messageId,
                    conversationId,
                    userId,
                    normalizedEmoji,
                    reactionRepository.countByMessageIdAndEmoji(messageId, normalizedEmoji),
                    "added"
            ));
        }

        messageCacheService.evictMessageCache(conversationId);
        return events;
    }

    private String normalizeEmoji(String emoji) {
        String normalized = emoji == null ? "" : emoji.trim();
        if (!ALLOWED_EMOJIS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported reaction emoji.");
        }
        return normalized;
    }
}
