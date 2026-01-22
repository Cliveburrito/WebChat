package com.example.WebChat.Repository;

import com.example.WebChat.Entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    Optional<Message> findFirstByConversation_ConversationIDOrderBySentAtDesc(
            Long conversationId);

    Page<Message> findByConversation_ConversationID(
            Long conversationId, org.springframework.data.domain.Pageable pageable);

    List<Message> findAllByConversation_ConversationIDOrderBySentAtAsc(
            Long conversationId);

    @Query(value = "SELECT * FROM messages WHERE conversation_id = :convId ORDER BY sent_at DESC LIMIT 1", nativeQuery = true)
    Optional<Message> findLastMessage(@Param("convId") Long convId);

}
