package com.example.WebChat.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * This table represents the link between USER and CONVERSATION.
 * Each row means:
 *     "This user is a member of this conversation."
 * Many-to-Many resolved as two Many-to-One links:
 *  - Many membership rows → one user
 *  - Many membership rows → one conversation
 */
@Entity
@Table(
        name = "conversation_membership",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "conversation_id"})
                // This ensures a user cannot join the same conversation twice.
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Primary key for each membership row
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    // FK to users.id (JPA generates the column for us)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    // FK to conversation.id
    private Conversation conversation;

    @Column(nullable = false)
    // When the user joined that conversation
    private Instant joinedAt;

    // Optional features
    private boolean muted;
    private boolean notificationsOn;

    // to show the number of unread messages :)
    @Builder.Default
    @Column(nullable = false)
    private int unreadCount = 0;
}
