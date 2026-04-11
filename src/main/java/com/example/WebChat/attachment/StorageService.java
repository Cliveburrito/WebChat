package com.example.WebChat.attachment;

import com.example.WebChat.attachment.dto.StoredFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface StorageService {
    // Stores the physical file and returns the validated metadata for persistence
    StoredFile store(MultipartFile file);

    // Loads the file from disk for downloading
    Path load(String filename);
}
