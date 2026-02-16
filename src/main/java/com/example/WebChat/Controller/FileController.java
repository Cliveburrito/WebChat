package com.example.WebChat.Controller;

import com.example.WebChat.DTO.AttachmentDTO;
import com.example.WebChat.DTO.CustomPrincipal;
import com.example.WebChat.Service.AttachmentService;
import com.example.WebChat.Service.FileSystemStorageService;
import com.example.WebChat.Service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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
    public ResponseEntity<Resource> downloadFile(@PathVariable String storageName) {
        // Fetch metadata to get the original filename
        AttachmentDTO metadata = attachmentService.getMetadataByStorageName(storageName);

        // Load the physical file
        Resource resource = storageService.loadAsResource(storageName);

        // Return with the CORRECT filename
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.originalName() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, metadata.contentType())
                .body(resource);
    }
}
