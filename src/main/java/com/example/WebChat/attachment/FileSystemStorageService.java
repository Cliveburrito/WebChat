package com.example.WebChat.attachment;

import com.example.WebChat.attachment.dto.StoredFile;
import com.example.WebChat.config.AppProperties;
import com.example.WebChat.shared.FileExceedsSizeException;
import com.example.WebChat.shared.UnsupportedFileTypeException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class FileSystemStorageService implements StorageService {

    private final Path rootLocation;
    private final long maxFileSize;
    private final Set<String> allowedContentTypes;
    private final int previewMaxDimension;
    private final Tika tika = new Tika();

    @Autowired
    public FileSystemStorageService(AppProperties properties) {
        this.rootLocation = Paths.get(properties.getStorage().getLocation());
        this.maxFileSize = properties.getStorage().getMaxFileSize();
        this.allowedContentTypes = new HashSet<>(properties.getStorage().getAllowedContentTypes());
        this.previewMaxDimension = properties.getStorage().getPreviewMaxDimension();
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
    public StoredFile store(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains("..")) {
            throw new RuntimeException("Security Breach: Cannot store file with relative path " + originalFilename);
        }

        if (file.getSize() > maxFileSize) {
            throw new FileExceedsSizeException("File size exceeds the allowed limit of " + (maxFileSize / 1024 / 1024) + "MB");
        }

        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Failed to store empty file.");
            }

            byte[] bytes = file.getBytes();
            String detectedContentType = detectAndValidateContentType(bytes, originalFilename);

            String storageName = generateStorageName(file.getOriginalFilename());
            String thumbnailStorageName = null;

            Path destinationFile = this.rootLocation.resolve(Paths.get(storageName))
                    .normalize().toAbsolutePath();

            if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
                throw new RuntimeException("Security Breach: Cannot store file outside of the root directory.");
            }

            try (InputStream inputStream = new java.io.ByteArrayInputStream(bytes)) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            if (detectedContentType.startsWith("image/")) {
                thumbnailStorageName = generateThumbnail(bytes);
            }

            log.info("File stored {} ({})", destinationFile.getFileName(), detectedContentType);
            return new StoredFile(storageName, detectedContentType, file.getSize(), thumbnailStorageName);
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

    private String detectAndValidateContentType(byte[] bytes, String originalFilename) {
        String detectedContentType = tika.detect(bytes, originalFilename);

        if (detectedContentType == null || detectedContentType.isBlank()) {
            throw new UnsupportedFileTypeException("Could not determine the uploaded file type.");
        }

        if (!allowedContentTypes.contains(detectedContentType)) {
            throw new UnsupportedFileTypeException("Unsupported file type: " + detectedContentType);
        }

        return detectedContentType;
    }

    private String generateThumbnail(byte[] originalBytes) throws IOException {
        BufferedImage sourceImage = ImageIO.read(new java.io.ByteArrayInputStream(originalBytes));
        if (sourceImage == null) {
            return null;
        }

        int originalWidth = sourceImage.getWidth();
        int originalHeight = sourceImage.getHeight();
        int longestSide = Math.max(originalWidth, originalHeight);
        if (longestSide <= previewMaxDimension) {
            return null;
        }

        double scale = (double) previewMaxDimension / longestSide;
        int targetWidth = Math.max(1, (int) Math.round(originalWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(originalHeight * scale));

        BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaledImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(sourceImage, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        String thumbnailStorageName = UUID.randomUUID() + "-preview.jpg";
        Path thumbnailPath = this.rootLocation.resolve(thumbnailStorageName).normalize().toAbsolutePath();
        if (!thumbnailPath.getParent().equals(this.rootLocation.toAbsolutePath())) {
            throw new RuntimeException("Security Breach: Cannot store preview outside of the root directory.");
        }

        ImageIO.write(scaledImage, "jpg", thumbnailPath.toFile());
        return thumbnailStorageName;
    }
}
