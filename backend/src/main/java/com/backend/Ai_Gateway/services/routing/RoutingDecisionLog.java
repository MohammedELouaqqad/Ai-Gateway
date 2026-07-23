package com.backend.Ai_Gateway.services.routing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "routing_decision_log")
@Getter
@Setter
@NoArgsConstructor
public class RoutingDecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant createdAt;
    private Long userId;
    private String mode;
    private String profile;
    private String selectedModelName;
    private String appliedModelName;
    private String fallbackModelName;
    private double selectedEstimatedCostEur;
    private double appliedEstimatedCostEur;
    private boolean diverged;
    private int promptChars;

    @Column(length = 2000)
    private String rankedJson;

    @Column(length = 2000)
    private String eliminatedJson;
}
