package com.example.WebChat.Repository;

import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
        SELECT new com.example.WebChat.DTO.ChatMessageResponse(
            m.message,
            m.sentAt,
            s.username,
            c.conversationID,
            null
        )
        FROM Message m
        JOIN m.sender s
        JOIN m.conversation c
        WHERE c.conversationID = :convId
    """)
    Slice<ChatMessageResponse> findByConversationIdOptimized(@Param("convId") Long convId, Pageable pageable);
}
