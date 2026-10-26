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
    /** Admin-configurable — NOT inferred from provider type (unlike streaming), because
     *  vision support varies model-by-model within the same provider (e.g. gpt-4o vs
     *  gpt-3.5-turbo, both OPENAI). Defaults false: an existing model stays text-only until
     *  an admin explicitly marks it vision-capable.
     *  <p>{@code columnDefinition} is required, not cosmetic: a plain {@code boolean} maps to
     *  {@code NOT NULL} with no default, and {@code ddl-auto=update} then emits a bare
     *  {@code ADD COLUMN ... NOT NULL} — which Postgres rejects on a non-empty table ("column
     *  contains null values"). The explicit default lets existing rows (e.g. the seed data)
     *  backfill to {@code false} instead. Found by actually running this migration against a
     *  seeded database, not by inspection. */
    @Column(columnDefinition = "boolean not null default false")
    private boolean visionCapable;
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
