package com.example.WebChat.Repository;

import com.example.WebChat.Entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Integer> {

    @Query("SELECT a FROM Attachment a JOIN FETCH a.uploadedBy WHERE a.storageName = :storageName")
    Optional<Attachment> findByStorageNameWithUploader(@Param("storageName") String storageName);
}