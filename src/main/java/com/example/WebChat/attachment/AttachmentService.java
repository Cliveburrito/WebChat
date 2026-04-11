package com.example.WebChat.attachment;

import com.example.WebChat.attachment.dto.AttachmentDTO;
import com.example.WebChat.attachment.dto.AttachmentAccessMetadata;
import com.example.WebChat.attachment.dto.AttachmentAccessRow;
import com.example.WebChat.attachment.dto.FileLinkTask;
import com.example.WebChat.attachment.dto.StoredFile;
import com.example.WebChat.conversation.MembershipGuard;
import com.example.WebChat.shared.ResourceNotFoundException;
import com.example.WebChat.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class AttachmentService {
    private final StorageService storageService;
    private final AttachmentRepository attachmentRepository;
    private final MembershipGuard  membershipGuard;
    private final RabbitTemplate rabbitTemplate;


    @Cacheable(value = "attachment_access", key = "#storageName")
    public AttachmentAccessMetadata getAccessMetadata(String storageName) {
        AttachmentAccessRow row = attachmentRepository.findAccessMetadataByStorageName(storageName)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment with storage name " + storageName + " not found"));

        return new AttachmentAccessMetadata(
                row.getConversationId(),
                row.getStorageName(),
                row.getOriginalName(),
                row.getContentType(),
                row.getThumbnailUrl()
        );
    }

    public AttachmentAccessMetadata getMetadataByStorageNameForUser(String storageName, Long userId) {
        AttachmentAccessMetadata metadata = getAccessMetadata(storageName);

        if (!membershipGuard.isMember(userId, metadata.conversationId())) {
            throw new AccessDeniedException("You do not have access to this attachment.");
        }

        return metadata;
    }

    public void handleAsyncUpload(List<MultipartFile> files, Long messageId, Long conversationId, Long id) {
        if (!membershipGuard.isMember(id, conversationId)) {
            throw new AccessDeniedException("You do not have access to upload files in this conversation.");
        }

        List<StoredFile> storedFiles = files.stream()
                .map(storageService::store)
                .toList();

        List<String> storageNames = storedFiles.stream()
                .map(StoredFile::storageName)
                .toList();

        List<String> originalNames = files.stream()
                .map(f -> sanitizeOriginalFilename(f.getOriginalFilename()))
                .toList();

        List<String> contentTypes = storedFiles.stream()
                .map(StoredFile::contentType)
                .toList();

        List<Long> fileSizes = storedFiles.stream()
                .map(StoredFile::fileSize)
                .toList();

        List<String> thumbnailStorageNames = storedFiles.stream()
                .map(StoredFile::thumbnailStorageName)
                .toList();

        FileLinkTask task = new FileLinkTask(
                messageId,
                conversationId,
                id,
                storageNames,
                originalNames,
                contentTypes,
                fileSizes,
                thumbnailStorageNames
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.CHAT_EXCHANGE, RabbitMQConfig.FILE_LINK_ROUTING_KEY, task);
    }

    public AttachmentAccessMetadata getPreviewMetadataForUser(String storageName, Long userId) {
        return getMetadataByStorageNameForUser(storageName, userId);
    }

    public void verifyConversationAccess(Long conversationId, Long userId) {
        if (!membershipGuard.isMember(userId, conversationId)) {
            throw new AccessDeniedException("You do not have access to this attachment.");
        }
    }

    private static String sanitizeOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }

        String normalized = originalFilename.replace('\\', '/');
        return Paths.get(normalized).getFileName().toString();
    }

    public List<AttachmentDTO> getGallery(Long conversationId, Long userId) {
        if (!membershipGuard.isMember(userId, conversationId)) {
            throw new AccessDeniedException("Δεν έχεις πρόσβαση στο gallery αυτής της συνομιλίας.");
        }

        return attachmentRepository.findAllByConversationId(conversationId)
                .stream()
                .map(AttachmentDTO::fromEntity)
                .toList();
    }
}
