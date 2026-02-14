package com.example.WebChat.Repository;

import com.example.WebChat.DTO.ChatListRow;
import com.example.WebChat.Entity.ConvMembership;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ConvMembershipRepository extends JpaRepository<ConvMembership, Long> {
    @Query("SELECT m1.conversation.conversationID " +
            "FROM ConvMembership m1 " +
            "JOIN ConvMembership m2 ON m1.conversation.conversationID = m2.conversation.conversationID " +
            "WHERE m1.user.id = :user1Id " +
            "AND m2.user.id = :user2Id " +
            "AND m1.conversation.isGroup = false")
    Optional<Long> findExistingDirectChatId(@Param("user1Id") Long user1Id,
                                            @Param("user2Id") Long user2Id);

    @Query("SELECT COUNT(m) > 0 FROM ConvMembership m " +
            "WHERE m.user.id = :userId " +
            "AND m.conversation.conversationID = :convId")
    boolean existsByUserIdAndConvId(@Param("userId") Long userId,
                                    @Param("convId") Long convId);

    @Query(value = """
SELECT
    c.conversationid AS conversation_id,

    CASE
        WHEN c.is_group = true THEN c.conversation_name
        ELSE COALESCE(other_u.username, 'Direct Chat')
    END AS displayName,

    -- FIX 1: Use 'lm.display_content', NOT 'lm.message'
    COALESCE(lm.display_content, 'No messages yet') AS lastContent,

    lm.sent_at AS lastMessageAt,
    me.unread_count AS unreadCount,
    me.muted AS muted

FROM conversations c

JOIN conversation_membership me
  ON me.conversation_id = c.conversationid
 AND me.user_id = :userId

LEFT JOIN conversation_membership other_m
  ON c.is_group = false
 AND other_m.conversation_id = c.conversationid
 AND other_m.user_id <> :userId

LEFT JOIN users other_u
  ON c.is_group = false
 AND other_u.id = other_m.user_id

LEFT JOIN LATERAL (
    SELECT
            CASE
                WHEN m.message IS NOT NULL AND m.message != '' THEN m.message
                -- FIX 2: Ensure this table name matches your DB (attachment vs attachments)
                WHEN EXISTS (SELECT 1 FROM attachment a WHERE a.message_id = m.id) THEN 'Attachment'
                ELSE 'Empty message'
            END as display_content,
            m.sent_at
        FROM messages m
        WHERE m.conversation_id = c.conversationid
        ORDER BY m.sent_at DESC
        LIMIT 1
) lm ON true

ORDER BY lm.sent_at DESC NULLS LAST
""", nativeQuery = true)
    List<ChatListRow> findUserChats(@Param("userId") Long userId);


    @Modifying
    @Transactional
    @Query(value = "UPDATE conversation_membership SET unread_count = unread_count + 1 " +
            "WHERE conversation_id = :convId AND user_id != :senderId",
            nativeQuery = true)
    void incrementUnreadCountForOthers(@Param("convId") Long convId, @Param("senderId") Long senderId);

    @Modifying
    @Query("UPDATE ConvMembership cm SET cm.unreadCount = 0 " +
            "WHERE cm.conversation.conversationID = :convId AND cm.user.id = :userId")
    void resetUnreadCount(@Param("convId") Long convId, @Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE ConvMembership cm SET cm.muted = :status " +
            "WHERE cm.conversation.conversationID = :convId AND cm.user.id = :userId")
    void toggleMute(@Param("userId") Long userId, @Param("convId") Long convId, @Param("status") boolean status);
}

