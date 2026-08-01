package com.backend.Ai_Gateway.services.routing;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RoutingDecisionLogRepository extends JpaRepository<RoutingDecisionLog, Long> {

    List<RoutingDecisionLog> findByCreatedAtBetweenAndMode(Instant from, Instant to, String mode);

    @Query("""
            select l from RoutingDecisionLog l
            where l.diverged = true
              and l.judgedAt is null
              and l.promptSnapshot is not null
              and l.appliedResponseSnapshot is not null
              and l.selectedModelName is not null
              and (:mode is null or l.mode = :mode)
            order by l.createdAt desc
            """)
    List<RoutingDecisionLog> findJudgeReadySample(@Param("mode") String mode, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            update RoutingDecisionLog l
            set l.promptSnapshot = null,
                l.appliedResponseSnapshot = null,
                l.proposedResponseSnapshot = null
            where l.createdAt < :cutoff
              and (l.promptSnapshot is not null
                   or l.appliedResponseSnapshot is not null
                   or l.proposedResponseSnapshot is not null)
            """)
    int clearSnapshotsOlderThan(@Param("cutoff") Instant cutoff);
}
