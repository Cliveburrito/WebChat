package com.example.WebChat.Service;

import com.example.WebChat.DTO.AttachmentDTO;
import com.example.WebChat.DTO.FileLinkTask;
import com.example.WebChat.Exception.RateLimitExceededException;
import com.example.WebChat.Exception.ResourceNotFoundException;
import com.example.WebChat.Repository.AttachmentRepository;
import com.example.WebChat.UtilsConfigs.RabbitMQConfig;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

    private final RabbitTemplate rabbitTemplate;
    private final RateLimiterService rateLimiter;


    public AttachmentDTO getMetadataByStorageName(String storageName) {
        return attachmentRepository.findByStorageNameWithUploader(storageName)
                .map(AttachmentDTO::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment with storage name " + storageName + " not found"));
    }

    public void handleAsyncUpload(List<MultipartFile> files, Long messageId, Long conversationId, Long id) {
        // Physical Storage (Outside Transaction)
        List<String> storageNames = files.stream()
                .map(storageService::store) // Save to disk
                .toList();

        List<String> originalNames = files.stream()
                .map(f -> sanitizeOriginalFilename(f.getOriginalFilename()))
                .toList();


        // Hand off to RabbitMQ , delegate the heavy db work to the consumer basically
        FileLinkTask task = new FileLinkTask(messageId, conversationId, storageNames, originalNames);
        rabbitTemplate.convertAndSend(RabbitMQConfig.CHAT_EXCHANGE, RabbitMQConfig.FILE_LINK_ROUTING_KEY, task);
    }

    private static String sanitizeOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }

        String normalized = originalFilename.replace('\\', '/');
        return Paths.get(normalized).getFileName().toString();
    }


}
