package com.backend.Ai_Gateway.entity;

import com.backend.Ai_Gateway.enums.FeedbackRating;
import com.backend.Ai_Gateway.enums.FeedbackSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "message_feedback",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_message_feedback_message_user",
                columnNames = {"message_id", "user_id"}
        ),
        indexes = {
                @Index(name = "idx_message_feedback_user", columnList = "user_id"),
                @Index(name = "idx_message_feedback_model", columnList = "model_name")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class MessageFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FeedbackRating rating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FeedbackSource source = FeedbackSource.EXPLICIT;

    @Column(length = 500)
    private String reason;

    /** Snapshot of applied model at feedback time (for aggregation if message model changes). */
    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
