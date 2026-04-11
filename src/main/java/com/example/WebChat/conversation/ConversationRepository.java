package com.example.WebChat.conversation;

import com.example.WebChat.conversation.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;


public interface ConversationRepository extends JpaRepository<Conversation, Long> {

}
