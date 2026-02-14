package com.example.WebChat;

import com.example.WebChat.Exception.FileExceedsSizeException;
import com.example.WebChat.Service.FileSystemStorageService;
import com.example.WebChat.UtilsConfigs.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemStorageServiceTest {

    private FileSystemStorageService storageService;
    private AppProperties appProperties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getStorage().setLocation(tempDir.toString());
        appProperties.getStorage().setMaxFileSize(5 * 1024 * 1024); // 5MB

        storageService = new FileSystemStorageService(appProperties);
        storageService.init();
    }

    @Nested
    @DisplayName("store Tests")
    class StoreTests {

        @Test
        @DisplayName("Should store file successfully")
        void shouldStoreFile() throws IOException {
            // Given
            byte[] content = "Hello, World!".getBytes();
            MultipartFile file = new MockMultipartFile(
                    "test.txt",
                    "test.txt",
                    "text/plain",
                    content
            );

            // When
            String storageName = storageService.store(file);

            // Then
            assertThat(storageName).isNotNull();
            assertThat(storageName).endsWith(".txt");
            assertThat(storageName).hasSize(36 + 4); // UUID (36) + .txt (4)

            Path storedFile = tempDir.resolve(storageName);
            assertThat(Files.exists(storedFile)).isTrue();
            assertThat(Files.readAllBytes(storedFile)).isEqualTo(content);
        }

        @Test
        @DisplayName("Should store file without extension")
        void shouldStoreFileWithoutExtension() throws IOException {
            // Given
            byte[] content = "No extension".getBytes();
            MultipartFile file = new MockMultipartFile(
                    "test",
                    "test",
                    "text/plain",
                    content
            );

            // When
            String storageName = storageService.store(file);

            // Then
            assertThat(storageName).isNotNull();
            assertThat(storageName).doesNotContain(".");
            assertThat(storageName).hasSize(36); // UUID only

            Path storedFile = tempDir.resolve(storageName);
            assertThat(Files.exists(storedFile)).isTrue();
        }

        @Test
        @DisplayName("Should throw FileExceedsSizeException when file too large")
        void shouldThrowWhenFileTooLarge() {
            // Given
            byte[] content = new byte[6 * 1024 * 1024]; // 6MB > 5MB limit
            MultipartFile file = new MockMultipartFile(
                    "large.txt",
                    "large.txt",
                    "text/plain",
                    content
            );

            // When/Then
            assertThatThrownBy(() -> storageService.store(file))
                    .isInstanceOf(FileExceedsSizeException.class)
                    .hasMessageContaining("exceeds the allowed limit");
        }

        @Test
        @DisplayName("Should throw RuntimeException when file empty")
        void shouldThrowWhenFileEmpty() {
            // Given
            byte[] content = new byte[0];
            MultipartFile file = new MockMultipartFile(
                    "empty.txt",
                    "empty.txt",
                    "text/plain",
                    content
            );

            // When/Then
            assertThatThrownBy(() -> storageService.store(file))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to store empty file");
        }

        @Test
        @DisplayName("Should prevent directory traversal attack")
        void shouldPreventDirectoryTraversal() {
            // Given
            byte[] content = "test".getBytes();
            MultipartFile file = new MockMultipartFile(
                    "malicious.txt",
                    "../malicious.txt",
                    "text/plain",
                    content
            );

            // When/Then
            assertThatThrownBy(() -> storageService.store(file))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Security Breach");
        }
    }

    @Nested
    @DisplayName("loadAsResource Tests")
    class LoadAsResourceTests {

        @Test
        @DisplayName("Should load existing file as resource")
        void shouldLoadExistingFile() throws IOException {
            // Given
            String fileName = "test-file.txt";
            byte[] content = "Test content".getBytes();
            Path testFile = tempDir.resolve(fileName);
            Files.write(testFile, content);

            // When
            Resource resource = storageService.loadAsResource(fileName);

            // Then
            assertThat(resource).isNotNull();
            assertThat(resource.exists()).isTrue();
            assertThat(resource.isReadable()).isTrue();
            assertThat(resource.getFilename()).isEqualTo(fileName);
            assertThat(resource.getContentAsByteArray()).isEqualTo(content);
        }

        @Test
        @DisplayName("Should throw RuntimeException when file not found")
        void shouldThrowWhenFileNotFound() {
            // Given
            String nonExistentFile = "does-not-exist.txt";

            // When/Then
            assertThatThrownBy(() -> storageService.loadAsResource(nonExistentFile))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Could not read file: " + nonExistentFile);
        }
    }

    @Nested
    @DisplayName("load Tests")
    class LoadTests {

        @Test
        @DisplayName("Should return correct path for filename")
        void shouldReturnPath() {
            // Given
            String filename = "test.txt";

            // When
            Path result = storageService.load(filename);

            // Then
            assertThat(result).isEqualTo(tempDir.resolve(filename));
        }
    }

    @Nested
    @DisplayName("generateStorageName Tests")
    class GenerateStorageNameTests {

        @Test
        @DisplayName("Should generate unique storage names")
        void shouldGenerateUniqueNames() throws IOException {
            // Given
            MultipartFile file1 = new MockMultipartFile("file1", "test1.jpg", "image/jpeg", "content1".getBytes());
            MultipartFile file2 = new MockMultipartFile("file2", "test2.jpg", "image/jpeg", "content2".getBytes());

            // When
            String name1 = storageService.store(file1);
            String name2 = storageService.store(file2);

            // Then
            assertThat(name1).isNotEqualTo(name2);
            assertThat(name1).endsWith(".jpg");
            assertThat(name2).endsWith(".jpg");
        }

        @Test
        @DisplayName("Should preserve original extension")
        void shouldPreserveExtension() throws IOException {
            // Given
            MultipartFile jpgFile = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "content".getBytes());
            MultipartFile pdfFile = new MockMultipartFile("file", "doc.pdf", "application/pdf", "content".getBytes());
            MultipartFile noExtFile = new MockMultipartFile("file", "README", "text/plain", "content".getBytes());

            // When
            String jpgName = storageService.store(jpgFile);
            String pdfName = storageService.store(pdfFile);
            String noExtName = storageService.store(noExtFile);

            // Then
            assertThat(jpgName).endsWith(".jpg");
            assertThat(pdfName).endsWith(".pdf");
            assertThat(noExtName).doesNotContain(".");
        }
    }

    @Nested
    @DisplayName("init Tests")
    class InitTests {

        @Test
        @DisplayName("Should create directory if not exists")
        void shouldCreateDirectory() throws IOException {
            // Given
            Path newDir = tempDir.resolve("new-storage");
            appProperties.getStorage().setLocation(newDir.toString());
            storageService = new FileSystemStorageService(appProperties);

            // When
            storageService.init();

            // Then
            assertThat(Files.exists(newDir)).isTrue();
            assertThat(Files.isDirectory(newDir)).isTrue();
        }

        @Test
        @DisplayName("Should not throw if directory already exists")
        void shouldNotThrowIfDirectoryExists() {
            // Given - directory already exists from @TempDir
            appProperties.getStorage().setLocation(tempDir.toString());
            storageService = new FileSystemStorageService(appProperties);

            // When/Then
            storageService.init(); // Should not throw
            assertThat(Files.exists(tempDir)).isTrue();
        }
    }
}