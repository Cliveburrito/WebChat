package com.example.WebChat.Repository;

import com.example.WebChat.DTO.ChatMessageResponse;
import com.example.WebChat.Entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversation_ConversationID(
            Long conversationId, org.springframework.data.domain.Pageable pageable);


    // Optimized Paging fetches Message + Sender in ONE query
    @Query("SELECT new com.example.WebChat.DTO.ChatMessageResponse(m.message, m.sentAt, s.username, c.conversationID) " +
            "FROM Message m " +
            "JOIN m.sender s " +
            "JOIN m.conversation c " +
            "WHERE c.conversationID = :convId") // Clean and simple
    Page<ChatMessageResponse> findByConversationIdOptimized(@Param("convId") Long convId, Pageable pageable);


}
