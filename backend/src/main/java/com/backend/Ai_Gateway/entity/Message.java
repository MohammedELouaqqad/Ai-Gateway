package com.backend.Ai_Gateway.entity;


import com.backend.Ai_Gateway.enums.MessageRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private MessageRole role;

    @Column(columnDefinition = "TEXT")
    private String content;
    private Instant createdAt;

    /** Nom technique du modèle ayant généré la réponse (assistant uniquement). */
    @Column(length = 100)
    private String modelName;

    /** Libellé affiché du modèle (assistant uniquement). */
    @Column(length = 150)
    private String modelDisplayName;

    /** True when the user requested a regeneration of this assistant reply. */
    @Column(nullable = false)
    private boolean regenerated = false;

    /** Newer assistant message that replaced this one after regenerate (nullable). */
    private Long supersededByMessageId;

    @JsonIgnore
    @ManyToOne
    private Conversation conversation;
}
