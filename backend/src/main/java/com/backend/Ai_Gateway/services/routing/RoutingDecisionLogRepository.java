package com.backend.Ai_Gateway.services.routing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface RoutingDecisionLogRepository extends JpaRepository<RoutingDecisionLog, Long> {

    List<RoutingDecisionLog> findByCreatedAtBetweenAndMode(Instant from, Instant to, String mode);
}
