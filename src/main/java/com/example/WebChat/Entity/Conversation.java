package com.example.WebChat.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;


@Entity
@Table(name = "conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long conversationID;                // Unique ID of the conversation

    @Column(length = 100)

    private String conversationName;
    // For group chats this can be "Friends", "Work", etc.
    // For 1–1 chats you can keep it null or auto-generate something later.

    @Column(nullable = false)
    private boolean isGroup;
    // false = direct 1–1 chat, true = group chat

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    // When the conversation was created
}

