package com.backend.Ai_Gateway.entity;


import com.backend.Ai_Gateway.enums.RequestStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Getter
@Setter
public class UsageLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private double estimatedCost;
    private Long latencyMs;
    @Enumerated(EnumType.ORDINAL)
    private RequestStatus status;
    private Instant createdAt;

    @ManyToOne
    private User user;

    @JsonIgnore
    @ManyToOne
    private Provider provider;

    @ManyToOne
    private Model model;
}
