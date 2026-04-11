package com.example.WebChat.attachment;

import com.example.WebChat.conversation.Conversation;
import com.example.WebChat.message.Message;
import com.example.WebChat.user.User;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "attachment")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "storage_name", nullable = false, unique = true)
    private String storageName;

    @Column(name = "content_type")
    private String contentType; // "image/jpeg"

    @Column(name = "file_size")
    private Long fileSize; // in bytes

    @Column(name = "thumbnail_url")
    private String thumbnailUrl; // for a preview feature I want to implement
    // Tie it to a message
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private Message message;

    // Tie it to a chat
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    // Tie it to a user
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id")
    private User uploadedBy;
}
