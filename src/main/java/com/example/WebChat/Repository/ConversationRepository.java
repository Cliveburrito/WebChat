package com.example.WebChat.Repository;

import com.example.WebChat.Entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Conversation findByConversationID(Long conversationID);
}
