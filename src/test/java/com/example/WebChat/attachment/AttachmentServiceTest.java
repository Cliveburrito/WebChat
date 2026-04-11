package com.example.WebChat.attachment;

import com.example.WebChat.attachment.dto.AttachmentAccessMetadata;
import com.example.WebChat.attachment.dto.AttachmentAccessRow;
import com.example.WebChat.attachment.dto.FileLinkTask;
import com.example.WebChat.attachment.dto.StoredFile;
import com.example.WebChat.config.RabbitMQConfig;
import com.example.WebChat.conversation.MembershipGuard;
import com.example.WebChat.shared.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    private static final Long USER_ID = 5L;
    private static final Long CONVERSATION_ID = 6L;
    private static final Long MESSAGE_ID = 7L;

    @Mock
    private StorageService storageService;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private MembershipGuard membershipGuard;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AttachmentService attachmentService;

    @Test
    @DisplayName("handleAsyncUpload sanitizes filenames and publishes stored metadata")
    void handleAsyncUpload_sanitizesAndPublishes() {
        when(membershipGuard.isMember(USER_ID, CONVERSATION_ID)).thenReturn(true);

        MultipartFile file = new MockMultipartFile(
                "file",
                "C:\\Users\\eleni\\Desktop\\photo.jpg",
                "image/jpeg",
                "image".getBytes()
        );
        when(storageService.store(any(MultipartFile.class)))
                .thenReturn(new StoredFile("uuid-photo.jpg", "image/jpeg", 5L, "uuid-photo-thumb.jpg"));

        attachmentService.handleAsyncUpload(List.of(file), MESSAGE_ID, CONVERSATION_ID, USER_ID);

        ArgumentCaptor<FileLinkTask> taskCaptor = ArgumentCaptor.forClass(FileLinkTask.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.CHAT_EXCHANGE),
                eq(RabbitMQConfig.FILE_LINK_ROUTING_KEY),
                taskCaptor.capture()
        );

        FileLinkTask task = taskCaptor.getValue();
        assertThat(task.messageId()).isEqualTo(MESSAGE_ID);
        assertThat(task.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(task.uploaderId()).isEqualTo(USER_ID);
        assertThat(task.storageNames()).containsExactly("uuid-photo.jpg");
        assertThat(task.originalNames()).containsExactly("photo.jpg");
        assertThat(task.contentTypes()).containsExactly("image/jpeg");
        assertThat(task.fileSizes()).containsExactly(5L);
        assertThat(task.thumbnailStorageNames()).containsExactly("uuid-photo-thumb.jpg");
    }

    @Test
    @DisplayName("handleAsyncUpload rejects non-members before storing files")
    void handleAsyncUpload_rejectsNonMember() {
        when(membershipGuard.isMember(USER_ID, CONVERSATION_ID)).thenReturn(false);

        assertThatThrownBy(() -> attachmentService.handleAsyncUpload(List.of(), MESSAGE_ID, CONVERSATION_ID, USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("upload files");

        verifyNoInteractions(storageService, rabbitTemplate);
    }

    @Test
    @DisplayName("getMetadataByStorageNameForUser returns metadata for members only")
    void getMetadataByStorageNameForUser_returnsMetadataForMembers() {
        when(attachmentRepository.findAccessMetadataByStorageName("file-1")).thenReturn(Optional.of(row(
                CONVERSATION_ID,
                "file-1",
                "photo.jpg",
                "image/jpeg",
                "thumb-1"
        )));
        when(membershipGuard.isMember(USER_ID, CONVERSATION_ID)).thenReturn(true);

        AttachmentAccessMetadata metadata = attachmentService.getMetadataByStorageNameForUser("file-1", USER_ID);

        assertThat(metadata.storageName()).isEqualTo("file-1");
        assertThat(metadata.originalName()).isEqualTo("photo.jpg");
        assertThat(metadata.contentType()).isEqualTo("image/jpeg");
        assertThat(metadata.thumbnailStorageName()).isEqualTo("thumb-1");
    }

    @Test
    @DisplayName("getMetadataByStorageNameForUser rejects attachments outside the user's conversations")
    void getMetadataByStorageNameForUser_rejectsNonMember() {
        when(attachmentRepository.findAccessMetadataByStorageName("file-1")).thenReturn(Optional.of(row(
                CONVERSATION_ID,
                "file-1",
                "photo.jpg",
                "image/jpeg",
                "thumb-1"
        )));
        when(membershipGuard.isMember(USER_ID, CONVERSATION_ID)).thenReturn(false);

        assertThatThrownBy(() -> attachmentService.getMetadataByStorageNameForUser("file-1", USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("getAccessMetadata fails clearly when the attachment does not exist")
    void getAccessMetadata_failsClearlyWhenMissing() {
        when(attachmentRepository.findAccessMetadataByStorageName("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attachmentService.getAccessMetadata("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing");

        verify(membershipGuard, never()).isMember(any(), any());
    }

    private AttachmentAccessRow row(
            Long conversationId,
            String storageName,
            String originalName,
            String contentType,
            String thumbnailUrl
    ) {
        return new AttachmentAccessRow() {
            @Override
            public Long getConversationId() {
                return conversationId;
            }

            @Override
            public String getStorageName() {
                return storageName;
            }

            @Override
            public String getOriginalName() {
                return originalName;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public String getThumbnailUrl() {
                return thumbnailUrl;
            }
        };
    }
}
