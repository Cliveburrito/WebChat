package com.example.WebChat.attachment;

import com.example.WebChat.attachment.dto.AttachmentAccessRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Integer> {

    @Query("""
        SELECT a FROM Attachment a
        JOIN FETCH a.conversation
        LEFT JOIN FETCH a.message
        LEFT JOIN FETCH a.uploadedBy
        WHERE a.storageName = :storageName
    """)
    Optional<Attachment> findByStorageNameWithUploader(@Param("storageName") String storageName);

    @Query("""
        SELECT
            a.conversation.id AS conversationId,
            a.storageName AS storageName,
            a.originalName AS originalName,
            a.contentType AS contentType,
            a.thumbnailUrl AS thumbnailUrl
        FROM Attachment a
        WHERE a.storageName = :storageName
    """)
    Optional<AttachmentAccessRow> findAccessMetadataByStorageName(@Param("storageName") String storageName);

    @Query("""
        SELECT a FROM Attachment a
        JOIN FETCH a.conversation
        JOIN FETCH a.message m
        LEFT JOIN FETCH a.uploadedBy
        WHERE a.conversation.id = :conversationId
        ORDER BY m.sentAt DESC, a.id DESC
    """)
    List<Attachment> findAllByConversationId(@Param("conversationId") Long conversationId);
}
