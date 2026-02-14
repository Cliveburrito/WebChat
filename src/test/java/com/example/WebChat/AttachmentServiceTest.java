package com.example.WebChat;

import com.example.WebChat.DTO.AttachmentDTO;
import com.example.WebChat.DTO.FileLinkTask;
import com.example.WebChat.Entity.Attachment;
import com.example.WebChat.Entity.User;
import com.example.WebChat.Exception.RateLimitExceededException;
import com.example.WebChat.Exception.ResourceNotFoundException;
import com.example.WebChat.Repository.AttachmentRepository;
import com.example.WebChat.Service.AttachmentService;
import com.example.WebChat.Service.RateLimiterService;
import com.example.WebChat.Service.StorageService;
import com.example.WebChat.UtilsConfigs.RabbitMQConfig;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private StorageService storageService;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RateLimiterService rateLimiter;

    @Mock
    private Bucket bucket;

    @InjectMocks
    private AttachmentService attachmentService;

    @Captor
    private ArgumentCaptor<FileLinkTask> taskCaptor;

    private final Long USER_ID = 1L;
    private final Long CONVERSATION_ID = 100L;
    private final Long MESSAGE_ID = 500L;
    private final String STORAGE_NAME = "uuid-12345.jpg";
    private final String ORIGINAL_NAME = "vacation.jpg";

    @Nested
    @DisplayName("handleAsyncUpload Tests")
    class HandleAsyncUploadTests {

        @Test
        @DisplayName("Should process files and send to RabbitMQ")
        void shouldProcessFilesAndSendToRabbit() {
            // Given
            MockMultipartFile file1 = new MockMultipartFile(
                    "file1", "test1.jpg", "image/jpeg", "content1".getBytes()
            );
            MockMultipartFile file2 = new MockMultipartFile(
                    "file2", "test2.pdf", "application/pdf", "content2".getBytes()
            );
            List<MultipartFile> files = List.of(file1, file2);

            when(rateLimiter.resolveFileBucket(USER_ID)).thenReturn(bucket);
            when(bucket.tryConsume(2)).thenReturn(true);

            String storageName1 = "uuid-111.jpg";
            String storageName2 = "uuid-222.pdf";
            when(storageService.store(file1)).thenReturn(storageName1);
            when(storageService.store(file2)).thenReturn(storageName2);

            // When
            attachmentService.handleAsyncUpload(files, MESSAGE_ID, CONVERSATION_ID, USER_ID);

            // Then
            verify(rabbitTemplate).convertAndSend(
                    eq(RabbitMQConfig.CHAT_EXCHANGE),
                    eq(RabbitMQConfig.FILE_LINK_ROUTING_KEY),
                    taskCaptor.capture()
            );

            FileLinkTask capturedTask = taskCaptor.getValue();
            assertThat(capturedTask.messageId()).isEqualTo(MESSAGE_ID);
            assertThat(capturedTask.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(capturedTask.storageNames()).containsExactly(storageName1, storageName2);
            assertThat(capturedTask.originalNames()).containsExactly("test1.jpg", "test2.pdf");
        }

        @Test
        @DisplayName("Should throw RateLimitExceeded when limit exceeded")
        void shouldThrowWhenRateLimitExceeded() {
            // Given
            MockMultipartFile file1 = new MockMultipartFile("file1", "test.jpg", "image/jpeg", "content".getBytes());
            MockMultipartFile file2 = new MockMultipartFile("file2", "test2.jpg", "image/jpeg", "content2".getBytes());
            List<MultipartFile> files = List.of(file1, file2);
            FileLinkTask task = new FileLinkTask(1L, 1L , List.of("storage"), List.of("real"));

            when(rateLimiter.resolveFileBucket(USER_ID)).thenReturn(bucket);
            when(bucket.tryConsume(2)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() ->
                    attachmentService.handleAsyncUpload(files, MESSAGE_ID, CONVERSATION_ID, USER_ID))
                    .isInstanceOf(RateLimitExceededException.class)
                    .hasMessageContaining("File upload limit reached");

            verify(storageService, never()).store(any());
            verify(rabbitTemplate, never()).convertAndSend(RabbitMQConfig.CHAT_EXCHANGE, RabbitMQConfig.FILE_LINK_ROUTING_KEY, task);
        }

        @Test
        @DisplayName("Should handle empty file list")
        void shouldHandleEmptyFileList() {
            // Given
            List<MultipartFile> emptyFiles = List.of();

            when(rateLimiter.resolveFileBucket(USER_ID)).thenReturn(bucket);
            when(bucket.tryConsume(0)).thenReturn(true);

            // When
            attachmentService.handleAsyncUpload(emptyFiles, MESSAGE_ID, CONVERSATION_ID, USER_ID);

            // Then
            verify(rabbitTemplate).convertAndSend(
                    anyString(),
                    anyString(),
                    taskCaptor.capture()
            );

            FileLinkTask capturedTask = taskCaptor.getValue();
            assertThat(capturedTask.storageNames()).isEmpty();
            assertThat(capturedTask.originalNames()).isEmpty();
        }

        @Test
        @DisplayName("Should handle file with path in original name")
        void shouldHandleFilePathInOriginalName() {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "C:\\Users\\test\\images\\vacation.jpg",
                    "image/jpeg",
                    "content".getBytes()
            );
            List<MultipartFile> files = List.of(file);

            when(rateLimiter.resolveFileBucket(USER_ID)).thenReturn(bucket);
            when(bucket.tryConsume(1)).thenReturn(true);
            when(storageService.store(file)).thenReturn(STORAGE_NAME);

            // When
            attachmentService.handleAsyncUpload(files, MESSAGE_ID, CONVERSATION_ID, USER_ID);

            // Then
            verify(rabbitTemplate).convertAndSend(
                    anyString(),
                    anyString(),
                    taskCaptor.capture()
            );

            FileLinkTask capturedTask = taskCaptor.getValue();
            assertThat(capturedTask.originalNames().getFirst()).isEqualTo("vacation.jpg"); // Extracted filename only
        }
    }

    @Nested
    @DisplayName("getMetadataByStorageName Tests")
    class GetMetadataByStorageNameTests {

        @Test
        @DisplayName("Should return metadata when attachment exists")
        void shouldReturnMetadata() {
            // Given
            User uploader = User.builder().id(1L).username("testuser").build();

            Attachment attachment = Attachment.builder()
                    .id(1)
                    .storageName(STORAGE_NAME)
                    .originalName(ORIGINAL_NAME)
                    .contentType("image/jpeg")
                    .fileSize(1024L)
                    .thumbnailUrl("/thumbnails/123.jpg")
                    .uploadedBy(uploader)
                    .build();

            when(attachmentRepository.findByStorageNameWithUploader(STORAGE_NAME))
                    .thenReturn(Optional.of(attachment));

            // When
            AttachmentDTO result = attachmentService.getMetadataByStorageName(STORAGE_NAME);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1);
            assertThat(result.storageName()).isEqualTo(STORAGE_NAME);
            assertThat(result.originalName()).isEqualTo(ORIGINAL_NAME);
            assertThat(result.contentType()).isEqualTo("image/jpeg");
            assertThat(result.fileSize()).isEqualTo(1024L);
            assertThat(result.thumbnailUrl()).isEqualTo("/thumbnails/123.jpg");
            assertThat(result.uploadedBy()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when attachment not found")
        void shouldThrowWhenNotFound() {
            // Given
            when(attachmentRepository.findByStorageNameWithUploader(STORAGE_NAME))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() ->
                    attachmentService.getMetadataByStorageName(STORAGE_NAME))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Attachment with storage name " + STORAGE_NAME + " not found");
        }
    }


}