package com.backend.Ai_Gateway.repositories;

import com.backend.Ai_Gateway.entity.UsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface UsageLogRepository extends JpaRepository<UsageLog, Long>, JpaSpecificationExecutor<UsageLog> {
    List<UsageLog> findUsageLogByUserId(Long userId);

    List<UsageLog> findByUserIdAndCreatedAtBetween(Long userId, Instant start, Instant end);

    long countByUserIdAndCreatedAtBetween(Long userId, Instant start, Instant end);

    @Query("select coalesce(sum(u.estimatedCost), 0.0) from UsageLog u where u.user.id = :userId and u.createdAt >= :from and u.createdAt < :to")
    double sumEstimatedCostByUserIdAndCreatedAtBetween(
            @Param("userId") Long userId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("select coalesce(sum(u.estimatedCost), 0.0) from UsageLog u where u.createdAt >= :from and u.createdAt < :to")
    double sumEstimatedCostBetween(@Param("from") Instant from, @Param("to") Instant to);

    boolean existsByModel_Id(Long modelId);

    boolean existsByProvider_Id(Long providerId);
}
