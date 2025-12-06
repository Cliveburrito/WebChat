package com.example.WebChat.Repository;

import com.example.WebChat.Entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversation_ConversationID(Long conversationConversationID);
}
