package com.example.WebChat.Entity; // Package where this class lives

import jakarta.persistence.*;      // JPA annotations (Entity, Id, ManyToOne, etc.)
import lombok.*;                   // Lombok annotations to avoid boilerplate
import java.time.Instant;          // For storing timestamp info

/**
 * This class represents a single chat message in the system.
 * Each message:
 *  - belongs to exactly ONE conversation
 *  - is sent by exactly ONE user
 */
@Entity                              // Marks this class as a JPA entity (mapped to a table)
@Table(name = "messages")            // Explicit table name in the database (plural is common)
@Data                                // Lombok: generates getters, setters, equals, hashCode, toString
@Builder                             // Lombok: enables builder pattern for Message
@NoArgsConstructor                   // Lombok: generates a no-args constructor (required by JPA)
@AllArgsConstructor                  // Lombok: generates a constructor with all fields
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
     *
     * Many messages can be sent by the SAME user → ManyToOne.
     * JPA will create a foreign key column named "sender_id" in the "messages" table.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    // @JoinColumn defines the actual FK column in the "messages" table
    private User sender;

    /**
     * Reference to the CONVERSATION this message belongs to.
     *
     * Many messages belong to the SAME conversation → ManyToOne.
     * JPA will create a foreign key column named "conversation_id" in the "messages" table.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;
}
