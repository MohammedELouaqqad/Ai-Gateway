package com.backend.Ai_Gateway.entity;


import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Entity
@Getter
@Setter
@ToString(exclude = {"models", "usageLogs", "apiKey"})
@AllArgsConstructor
@NoArgsConstructor
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String baseUrl;
    @JsonIgnore
    private String apiKey;
    private String name;
    @Enumerated(EnumType.STRING)
    private ProviderType type;
    @Enumerated(EnumType.STRING)
    private ProviderLocation location;
    private boolean enabled;
    private Integer priority;
    private Instant createdAt;


    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "provider")
    private List<Model> models;

    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "provider")
    private List<UsageLog> usageLogs;

    /** Lets the UI show "key configured" without ever re-exposing the value itself. */
    @JsonProperty("apiKeyPresent")
    @Transient
    public boolean isApiKeyPresent() {
        return apiKey != null && !apiKey.isBlank();
    }
}
