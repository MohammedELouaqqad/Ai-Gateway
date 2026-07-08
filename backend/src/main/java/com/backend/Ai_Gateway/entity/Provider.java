package com.backend.Ai_Gateway.entity;


import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Entity
@Getter
@Setter
@ToString
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


}
