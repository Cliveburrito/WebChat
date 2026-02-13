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
import java.util.Objects;



@RequiredArgsConstructor
@Service
@Slf4j
public class AttachmentService {
    private final StorageService storageService;
    private final AttachmentRepository attachmentRepository;

    private final RabbitTemplate rabbitTemplate;
    private final RateLimiterService rateLimiter;


    public AttachmentDTO getMetadataByStorageName(String storageName) {
        return attachmentRepository.findByStorageName(storageName)
                .map(AttachmentDTO::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment with storage name " + storageName + " not found"));
    }

    public void handleAsyncUpload(List<MultipartFile> files, Long messageId, Long conversationId, String username) {
        Bucket bucket = rateLimiter.resolveFileBucket(username);

        // We consume tokens based on the NUMBER of files
        if (!bucket.tryConsume(files.size())) {
            log.warn("User {} is attempting to upload too many files!", username);
            throw new RateLimitExceededException("File upload limit reached. Please wait a minute.");
        }

        // Physical Storage (Outside Transaction)
        List<String> storageNames = files.stream()
                .map(storageService::store) // Save to disk
                .toList();

        List<String> originalNames = files.stream()
                .map(f -> Paths.get(Objects.requireNonNull(f.getOriginalFilename())).getFileName().toString())
                .toList();

        // Hand off to RabbitMQ , delegate the heavy db work to the consumer basically
        FileLinkTask task = new FileLinkTask(messageId, conversationId, storageNames, originalNames);
        rabbitTemplate.convertAndSend(RabbitMQConfig.CHAT_EXCHANGE, RabbitMQConfig.FILE_LINK_ROUTING_KEY, task);
    }

}
