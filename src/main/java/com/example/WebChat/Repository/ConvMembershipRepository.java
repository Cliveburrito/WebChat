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

    // Έλεγχος αν υπάρχει ήδη Direct Chat μεταξύ 2 χρηστών
    @Query("SELECT m1.conversation.conversationID " +
            "FROM ConvMembership m1 " +
            "JOIN ConvMembership m2 ON m1.conversation.conversationID = m2.conversation.conversationID " +
            "WHERE m1.user.id = :user1Id " +
            "AND m2.user.id = :user2Id " +
            "AND m1.conversation.isGroup = false")
    Optional<Long> findExistingDirectChatId(@Param("user1Id") Long user1Id,
                                            @Param("user2Id") Long user2Id);

    // Έλεγχος αν ο χρήστης ανήκει στη συνομιλία
    @Query("SELECT COUNT(m) > 0 FROM ConvMembership m " +
            "WHERE m.user.id = :userId " +
            "AND m.conversation.conversationID = :convId")
    boolean existsByUserIdAndConvId(@Param("userId") Long userId,
                                    @Param("convId") Long convId);

    // ==========================================
    // 🚀 THE BEAST QUERY (Updated)
    // ==========================================
    @Query(value = """
        SELECT
            c.conversationid AS conversation_id,
        
            -- Display Name Logic
            CASE
                WHEN c.is_group = true THEN c.conversation_name
                ELSE COALESCE(other_u.username, 'Unknown User')
            END AS displayName,
        
            -- Last Content: Αν είναι NULL, επιστρέφουμε κενό string
            COALESCE(lm.display_content, '') AS lastContent,
        
            lm.sent_at AS lastMessageAt,
        
            -- ✅ FIX: Unread Count με ασφάλεια για NULL values
            (SELECT COUNT(*)
             FROM messages m_unread
             WHERE m_unread.conversation_id = c.conversationid
             AND m_unread.id > COALESCE(me.last_read_message_id, 0)
             AND m_unread.sender_id <> :userId
            ) AS unreadCount,
        
            me.muted AS muted,
            c.is_group AS isGroup,  -- ✅ NEW: Χρειαζόμαστε το isGroup στο DTO
        
            -- ✅ NEW: Τα κλειδιά για τα Ticks και το "You:"
            lm.msg_id AS lastMessageId,
            lm.sender_id AS lastSenderId
        
        FROM conversations c
        
        JOIN conversation_membership me
          ON me.conversation_id = c.conversationid
         AND me.user_id = :userId
        
        -- Join για το όνομα του άλλου χρήστη (αν είναι Direct)
        LEFT JOIN conversation_membership other_m
          ON c.is_group = false
         AND other_m.conversation_id = c.conversationid
         AND other_m.user_id <> :userId
        
        LEFT JOIN users other_u
          ON c.is_group = false
         AND other_u.id = other_m.user_id
        
        -- Lateral Join για το τελευταίο μήνυμα
        LEFT JOIN LATERAL (
            SELECT
                m.id as msg_id,         -- ✅ Fetch ID
                m.sender_id,            -- ✅ Fetch Sender
                CASE
                    WHEN m.message IS NOT NULL AND m.message != '' THEN m.message
                    WHEN EXISTS (SELECT 1 FROM attachment a WHERE a.message_id = m.id) THEN 'Attachment'
                    ELSE ''
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


    // 2. Ενημέρωση Watermark: READ
    @Transactional
    @Modifying
    @Query("UPDATE ConvMembership cm SET cm.lastReadMessageId = :msgId " +
            "WHERE cm.conversation.conversationID = :convId AND cm.user.id = :userId")
    void updateLastReadId(@Param("userId") Long userId, @Param("convId") Long convId, @Param("msgId") Long msgId);

    // 3. Ενημέρωση Watermark: DELIVERED
    @Transactional
    @Modifying
    @Query("UPDATE ConvMembership cm SET cm.lastDeliveredMessageId = :msgId " +
            "WHERE cm.conversation.conversationID = :convId AND cm.user.id = :userId")
    void updateLastDeliveredId(@Param("userId") Long userId, @Param("convId") Long convId, @Param("msgId") Long msgId);

    // 4. Toggle Mute
    @Modifying
    @Transactional
    @Query("UPDATE ConvMembership cm SET cm.muted = :status " +
            "WHERE cm.conversation.conversationID = :convId AND cm.user.id = :userId")
    void toggleMute(@Param("userId") Long userId, @Param("convId") Long convId, @Param("status") boolean status);
}