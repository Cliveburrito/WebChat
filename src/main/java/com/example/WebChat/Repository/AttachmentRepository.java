package com.example.WebChat.Repository;

import com.example.WebChat.Entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Integer> {

    // for the file history
    List<Attachment> findByConversationConversationID(Long conversationId);

    // for the user to see his files
    List<Attachment> findByUploadedById(Long userId);

    // to find a file via the UUID
    Optional<Attachment> findByStorageName(String storageName);
}