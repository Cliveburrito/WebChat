package com.example.WebChat.message;

import com.example.WebChat.attachment.Attachment;
import com.example.WebChat.conversation.Conversation;
import com.example.WebChat.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.List;

/**
 * This class represents a single chat message in the system.
 * Each message:
 *  - belongs to exactly ONE conversation
 *  - is sent by exactly ONE user
 */
@Entity
@Table(name = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id                              // Marks this field as the primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Database will auto-generate the ID (e.g. SERIAL / IDENTITY column)
    private Long id;


    @Column(nullable = false, length = 500)
    // The actual text content of the message, max 500 characters, cannot be null
    private String message;

    // Maybe add an attachment later (separate field or entity)

    @Column(nullable = false)
    // When the message was sent; you can set this in code when creating the message
    private Instant sentAt;

    /**
     * Reference to the USER who sent this message.
     * <p>
     * Many messages can be sent by the SAME user -> ManyToOne.
     * JPA will create a foreign key column named "sender_id" in the "messages" table.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    // @JoinColumn defines the actual FK column in the "messages" table
    private User sender;

    /**
     * Reference to the CONVERSATION this message belongs to.
     * <p>
     * Many messages belong to the SAME conversation -> ManyToOne.
     * JPA will create a foreign key column named "conversation_id" in the "messages" table.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id")
    private Message replyToMessage;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL)
    @org.hibernate.annotations.BatchSize(size = 20) // <--- ΠΡΟΣΘΕΣΕ ΑΥΤΟ
    private List<Attachment> attachments;
}
