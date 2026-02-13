package com.example.WebChat.Service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface StorageService {
    // Stores the physical file and returns the generated storageName (UUID)
    String store(MultipartFile file);

    // Loads the file from disk for downloading
    Path load(String filename);
}
