package com.example.WebChat.Repository;

import com.example.WebChat.DTO.ChatListRow;
import com.example.WebChat.Entity.ConvMembership;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ConvMembershipRepository extends JpaRepository<ConvMembership, Long> {
    List<ConvMembership> findAllByUser_Id(
            Long userId);

    boolean existsByUser_IdAndConversation_ConversationID(
            Long userId, Long conversationId);

    boolean existsByUser_UsernameAndConversation_ConversationID(
            String username, Long conversationId);

    @Query("SELECT cm.user.username FROM ConvMembership cm WHERE cm.conversation.conversationID = :convId")
    List<String> findUsernamesByConversationId(@Param("convId") Long convId);

    @Query(value = """
    SELECT
        c.conversationid AS conversation_id,

        CASE
            WHEN c.is_group = true THEN c.conversation_name
            ELSE COALESCE(other_u.username, 'Direct Chat')
        END AS displayName,

        COALESCE(lm.message, 'No messages yet') AS lastContent,
        lm.sent_at AS lastMessageAt,
        me.unread_count AS unreadCount,
        me.muted AS muted

    FROM conversations c

    -- membership of current user
    JOIN conversation_membership me
      ON me.conversation_id = c.conversationid
     AND me.user_id = :userId

    -- other member ONLY for direct chats
            --we get the other userId from the convMembership entity
    LEFT JOIN conversation_membership other_m
      ON c.is_group = false
     AND other_m.conversation_id = c.conversationid
     AND other_m.user_id <> :userId
            -- we get the other user entity from the id we got above
    LEFT JOIN users other_u
      ON c.is_group = false
     AND other_u.id = other_m.user_id

    -- last message per conversation
    LEFT JOIN LATERAL (
        SELECT m.message, m.sent_at
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
    @Transactional
    @Query("UPDATE ConvMembership cm SET cm.unreadCount = 0 " +
            "WHERE cm.conversation.conversationID = :convId AND cm.user.username = :username")
    void resetUnreadCount(@Param("convId") Long convId, @Param("username") String username);

    @Modifying
    @Query(value = "UPDATE ConvMembership cm SET cm.muted = :status " +
            "WHERE cm.conversation.conversationID = :convId AND cm.user.username = :username")
    void toggleMute(@Param("username") String username, @Param("convId") Long convId, @Param("status") boolean status);
}

