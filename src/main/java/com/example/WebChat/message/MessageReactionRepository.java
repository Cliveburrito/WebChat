package com.example.WebChat.message;

import com.example.WebChat.message.dto.MessageReactionRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {
    List<MessageReaction> findByMessageIdAndUserId(Long messageId, Long userId);

    long countByMessageIdAndEmoji(Long messageId, String emoji);

    @Query("""
        SELECT new com.example.WebChat.message.dto.MessageReactionRow(
            r.message.id,
            r.emoji,
            COUNT(r.id),
            SUM(CASE WHEN r.user.id = :userId THEN 1 ELSE 0 END) > 0
        )
        FROM MessageReaction r
        WHERE r.message.id IN :messageIds
        GROUP BY r.message.id, r.emoji
        ORDER BY r.message.id ASC, COUNT(r.id) DESC, r.emoji ASC
    """)
    List<MessageReactionRow> summarizeForMessages(@Param("messageIds") Collection<Long> messageIds, @Param("userId") Long userId);
}
