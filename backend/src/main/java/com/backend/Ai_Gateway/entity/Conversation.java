package com.backend.Ai_Gateway.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Null = active. On delete, the row is kept with this timestamp set — an internal safety
     * margin, not a user-facing trash/undo feature (nothing restores it) — and purged for good
     * by {@link com.backend.Ai_Gateway.services.ConversationRetentionJob} once past the
     * retention window. To the user, the conversation is simply gone right away.
     */
    private Instant deletedAt;

    /** Owner — ignoré en JSON API pour éviter fuites / LazyInit ; chargé EAGER pour les checks ownership. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    private User user;

    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "conversation")
    private List<Message> messages;

    @ManyToOne(fetch = FetchType.EAGER)
    private Model model;


}
