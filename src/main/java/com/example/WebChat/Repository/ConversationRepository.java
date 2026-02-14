package com.example.WebChat.Repository;

import com.example.WebChat.Entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;


public interface ConversationRepository extends JpaRepository<Conversation, Long> {

}
