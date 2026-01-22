package com.example.WebChat.Repository;

import com.example.WebChat.Entity.ConvMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConvMembershipRepository extends JpaRepository<ConvMembership, Long> {
    List<ConvMembership> findAllByUser_Id(
            Long userId);

    List<ConvMembership> findAllByConversation_ConversationID(
            Long conversationId);

    boolean existsByUser_IdAndConversation_ConversationID(
            Long userId, Long conversationId);

    boolean existsByUser_UsernameAndConversation_ConversationID(
            String username, Long conversationId);

    Optional<ConvMembership> findByUser_UsernameAndConversation_ConversationID(
            String username, Long conversationId);

}
