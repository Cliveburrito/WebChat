package com.example.WebChat.Service;

import com.example.WebChat.UtilsConfigs.AppProperties;
import com.example.WebChat.Exception.FileExceedsSizeException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileSystemStorageService implements StorageService {

    private final Path rootLocation;
    private final long maxFileSize;

    @Autowired
    public FileSystemStorageService(AppProperties properties) {
        // Pulling values from the nested 'storage' group in AppProperties
        this.rootLocation = Paths.get(properties.getStorage().getLocation());
        this.maxFileSize = properties.getStorage().getMaxFileSize();
    }

    @PostConstruct
    public void init() {
        try {
            Path absolutePath = rootLocation.toAbsolutePath();
            log.info("Attempting to initialize storage at: {}", absolutePath);
            Files.createDirectories(absolutePath);
        } catch (IOException e) {
            // This will now tell you exactly WHICH path failed
            throw new RuntimeException("Could not initialize storage location: " + rootLocation.toAbsolutePath(), e);
        }
    }

    @Override
    public String store(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains("..")) {
            throw new RuntimeException("Security Breach: Cannot store file with relative path " + originalFilename);
        }

        // Validate file size using the value from application.yml
        if (file.getSize() > maxFileSize) {
            throw new FileExceedsSizeException("File size exceeds the allowed limit of " + (maxFileSize / 1024 / 1024) + "MB");
        }

        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Failed to store empty file.");
            }

            // Generate unique storage name UUID + original extension
            String storageName = generateStorageName(file.getOriginalFilename());

            // Normalize path and encapsulate directory traversal
            Path destinationFile = this.rootLocation.resolve(Paths.get(storageName))
                    .normalize().toAbsolutePath();

            if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
                throw new RuntimeException("Security Breach: Cannot store file outside of the root directory.");
            }

            // Stream the file to the disk
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("File stored  {} " , destinationFile.getFileName());
            return storageName; // This UUID string is what is saved in the 'storage_name' DB column
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + file.getOriginalFilename(), e);
        }
    }

    public Resource loadAsResource(String storageName) {
        try {
            // Get the local path
            Path file = load(storageName);

            //  Wrap it in a Resource
            Resource resource = new UrlResource(file.toUri());

            // Verify it's actually a file we can read
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read file: " + storageName);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Could not read file: " + storageName, e);
        }
    }

    @Override
    public Path load(String filename) {
        // Used by the controller to locate the file for downloading/streaming
        return rootLocation.resolve(filename);
    }

    /**
     * Helper to extract extension and prepend a UUID
     */
    private String generateStorageName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extension;
    }
}