package com.backend.Ai_Gateway.entity;


import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.provider.ProviderCapabilities;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Getter
@Setter
public class Model {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String displayName;
    private String name;
    private boolean enabled;
    @JsonProperty("isDefault")
    private boolean isDefault;
    private Instant createdAt;

    @ManyToOne
    private Provider provider;

    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "model")
    private List<Conversation> conversations;

    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "model")
    private List<UsageLog> usageLogs;

    /**
     * Adaptateur stream présent dans ce gateway (hérité du type de provider).
     * Tout nouveau modèle Ollama/OpenAI/Azure streame automatiquement.
     */
    @JsonProperty("supportsStreaming")
    @Transient
    public boolean isSupportsStreaming() {
        ProviderType type = provider != null ? provider.getType() : null;
        return ProviderCapabilities.hasStreamAdapter(type);
    }

    /**
     * {@code true} si ce gateway peut appeler le chat pour ce modèle
     * (adaptateur écrit). Reste listé même si {@code false} (catalogue transparent).
     */
    @JsonProperty("available")
    @Transient
    public boolean isAvailable() {
        ProviderType type = provider != null ? provider.getType() : null;
        return ProviderCapabilities.hasChatAdapter(type);
    }
}
