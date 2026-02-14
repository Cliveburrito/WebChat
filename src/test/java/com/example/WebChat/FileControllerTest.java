package com.example.WebChat;

import com.example.WebChat.Controller.FileController;
import com.example.WebChat.DTO.AttachmentDTO;
import com.example.WebChat.Exception.ResourceNotFoundException;
import com.example.WebChat.Service.AttachmentService;
import com.example.WebChat.Service.FileSystemStorageService;
import com.example.WebChat.Service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AttachmentService attachmentService;

    @MockitoBean
    private FileSystemStorageService storageService;

    private static final long TEST_USER_ID = 1L;
    private  final String TEST_USERNAME = "testuser";
    private final Long CONVERSATION_ID = 100L;
    private final Long MESSAGE_ID = 500L;
    private final String STORAGE_NAME = "uuid-12345.jpg";
    private final String ORIGINAL_NAME = "vacation.jpg";

    @Test
    @DisplayName("POST /api/files/upload - Should upload files successfully")
    @WithMockCustomUser()
    void handleFileUpload_ShouldReturnAccepted() throws Exception {
        // Given
        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "test1.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test image content 1".getBytes()
        );

        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "test2.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "test pdf content 2".getBytes()
        );

        doNothing().when(attachmentService).handleAsyncUpload(
                anyList(),
                eq(MESSAGE_ID),
                eq(CONVERSATION_ID),
                eq(TEST_USER_ID)
        );

        // When/Then
        mockMvc.perform(multipart("/api/files/upload")
                        .file(file1)
                        .file(file2)
                        .param("conversationId", String.valueOf(CONVERSATION_ID))
                        .param("messageId", String.valueOf(MESSAGE_ID))
                        .with(csrf())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isAccepted());

        verify(attachmentService).handleAsyncUpload(
                argThat(files -> files.size() == 2),
                eq(MESSAGE_ID),
                eq(CONVERSATION_ID),
                eq(TEST_USER_ID)
        );
    }

    @Test
    @DisplayName("POST /api/files/upload - Should handle single file upload")
    @WithMockCustomUser()
    void handleFileUpload_WithSingleFile_ShouldReturnAccepted() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test image content".getBytes()
        );

        doNothing().when(attachmentService).handleAsyncUpload(
                anyList(),
                eq(MESSAGE_ID),
                eq(CONVERSATION_ID),
                eq(TEST_USER_ID)
        );

        // When/Then
        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("conversationId", String.valueOf(CONVERSATION_ID))
                        .param("messageId", String.valueOf(MESSAGE_ID))
                        .with(csrf()))
                .andExpect(status().isAccepted());

        verify(attachmentService).handleAsyncUpload(
                argThat(files -> files.size() == 1),
                eq(MESSAGE_ID),
                eq(CONVERSATION_ID),
                eq(TEST_USER_ID)
        );
    }

    @Test
    @DisplayName("POST /api/files/upload - Should return 401 without authentication")
    void handleFileUpload_WithoutAuth_ShouldReturnUnauthorized() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test content".getBytes()
        );

        // When/Then
        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("conversationId", String.valueOf(CONVERSATION_ID))
                        .param("messageId", String.valueOf(MESSAGE_ID))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(attachmentService, never()).handleAsyncUpload(anyList(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("GET /api/files/download/{storageName} - Should download file")
    @WithMockCustomUser()
    void downloadFile_ShouldReturnFile() throws Exception {
        // Given
        AttachmentDTO metadata = new AttachmentDTO(
                1,
                STORAGE_NAME,
                ORIGINAL_NAME,
                MediaType.IMAGE_JPEG_VALUE,
                1024L,
                "/thumbnails/123.jpg",
                TEST_USERNAME,
                MESSAGE_ID,
                CONVERSATION_ID
        );

        Resource mockResource = mock(Resource.class);
        when(mockResource.getFilename()).thenReturn(STORAGE_NAME);

        when(attachmentService.getMetadataByStorageName(STORAGE_NAME)).thenReturn(metadata);
        when(storageService.loadAsResource(STORAGE_NAME)).thenReturn(mockResource);

        // When/Then
        mockMvc.perform(get("/api/files/download/{storageName}", STORAGE_NAME))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + ORIGINAL_NAME + "\""))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE));

        verify(attachmentService).getMetadataByStorageName(STORAGE_NAME);
        verify(storageService).loadAsResource(STORAGE_NAME);
    }

    @Test
    @DisplayName("GET /api/files/download/{storageName} - Should handle non-existent file")
    @WithMockCustomUser()
    void downloadFile_WithNonExistentFile_ShouldReturn404() throws Exception {
        // Given
        when(attachmentService.getMetadataByStorageName(STORAGE_NAME))
                .thenThrow(new ResourceNotFoundException("Attachment not found"));

        // When/Then
        mockMvc.perform(get("/api/files/download/{storageName}", STORAGE_NAME))
                .andExpect(status().isNotFound());

        verify(storageService, never()).loadAsResource(anyString());
    }

    @Test
    @DisplayName("GET /api/files/download/{storageName} - Should return 401 for anonymous users")
    void downloadFile_WithoutAuth_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/files/download/{storageName}", STORAGE_NAME))
                .andExpect(status().isUnauthorized());
    }
}