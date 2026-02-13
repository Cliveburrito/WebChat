package com.example.WebChat.Repository;

import com.example.WebChat.Entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
        SELECT DISTINCT m FROM Message m 
        LEFT JOIN FETCH m.sender s 
        LEFT JOIN FETCH m.attachments a 
        WHERE m.conversation.conversationID = :convId 
        ORDER BY m.sentAt DESC
    """)
    Slice<Message> findByConversationIdOptimized(@Param("convId") Long convId, Pageable pageable);
}

