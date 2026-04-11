package com.example.WebChat.message;

import com.example.WebChat.message.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
        SELECT DISTINCT m FROM Message m
        LEFT JOIN FETCH m.sender s
        LEFT JOIN FETCH m.attachments a
        WHERE m.conversation.id = :convId
        ORDER BY m.sentAt DESC
    """)
    Slice<Message> findByConversationIdOptimized(@Param("convId") Long convId, Pageable pageable);

    @Query("""
      SELECT DISTINCT m FROM Message m
      LEFT JOIN FETCH m.sender
      LEFT JOIN FETCH m.replyToMessage r
      LEFT JOIN FETCH r.sender
      LEFT JOIN FETCH m.attachments
      WHERE m.id IN :ids
      ORDER BY m.sentAt DESC
    """)
    List<Message> findMessagesWithDetails(@Param("ids") List<Long> ids);

    @Query("""
      SELECT m.id FROM Message m
      WHERE m.conversation.id = :convId
      ORDER BY m.sentAt DESC
    """)
    Slice<Long> findMessageIds(@Param("convId") Long convId, Pageable pageable);

    @Query("""
      SELECT m FROM Message m
      JOIN FETCH m.sender
      JOIN FETCH m.conversation
      LEFT JOIN FETCH m.replyToMessage r
      LEFT JOIN FETCH r.sender
      WHERE m.id = :id
    """)
    Optional<Message> findByIdWithSenderAndConversation(@Param("id") Long id);

    @Query("""
      SELECT m FROM Message m
      JOIN FETCH m.sender
      JOIN FETCH m.conversation
      WHERE m.id = :id
    """)
    Optional<Message> findByIdWithConversation(@Param("id") Long id);

    @Query(value = """
    SELECT m.id FROM messages m
    WHERE m.conversation_id = :convId
    AND to_tsvector('simple', coalesce(m.message, '')) @@ websearch_to_tsquery('simple', :query)
    ORDER BY ts_rank_cd(
        to_tsvector('simple', coalesce(m.message, '')),
        websearch_to_tsquery('simple', :query)
    ) DESC, m.sent_at DESC
    LIMIT 100
    """, nativeQuery = true)
    List<Long> searchMessageIds(@Param("convId") Long convId, @Param("query") String query);
}
