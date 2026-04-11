package com.example.WebChat.attachment;

import com.example.WebChat.attachment.dto.AttachmentDTO;
import com.example.WebChat.attachment.dto.AttachmentAccessMetadata;
import com.example.WebChat.auth.dto.CustomPrincipal;
import com.example.WebChat.attachment.AttachmentService;
import com.example.WebChat.attachment.FileSystemStorageService;
import com.example.WebChat.Service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api/files") //
@RequiredArgsConstructor
public class FileController {
    private final AttachmentService attachmentService;
    private final FileSystemStorageService storageService;
    private final RateLimiterService rateLimiter;

    @PostMapping("/upload")
    public ResponseEntity<Void> handleFileUpload(
            @RequestParam("file") List<MultipartFile> files,
            @RequestParam("conversationId") Long conversationId,
            @RequestParam("messageId") Long messageId,
            @AuthenticationPrincipal CustomPrincipal principal) {

        rateLimiter.consumeFileOrThrow(principal.id(), principal.username(), files.size(), conversationId, messageId);

        attachmentService.handleAsyncUpload(files, messageId, conversationId, principal.id() );
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/download/{storageName}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String storageName,
            @AuthenticationPrincipal CustomPrincipal principal
    ) {
        AttachmentAccessMetadata metadata = attachmentService.getMetadataByStorageNameForUser(storageName, principal.id());

        Resource resource = storageService.loadAsResource(storageName);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.originalName() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, metadata.contentType())
                .body(resource);
    }

    @GetMapping("/preview/{storageName}")
    public ResponseEntity<Resource> previewFile(
            @PathVariable String storageName,
            @RequestParam Long conversationId,
            @AuthenticationPrincipal CustomPrincipal principal
    ) {
        attachmentService.verifyConversationAccess(conversationId, principal.id());

        Resource resource = storageService.loadAsResource(storageName);
        String contentType = "application/octet-stream";
        try {
            String detected = Files.probeContentType(storageService.load(storageName));
            if (detected != null && !detected.isBlank()) {
                contentType = detected;
            }
        } catch (Exception ignored) {
            // Fall back to application/octet-stream if probing fails.
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .body(resource);
    }

    @GetMapping("/{conversationId}/gallery")
    public ResponseEntity<List<AttachmentDTO>> gallery(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal CustomPrincipal principal
    ) {
        List<AttachmentDTO> attachments = attachmentService.getGallery(conversationId, principal.getUserId());

        return ResponseEntity.ok(attachments);
    }
}
