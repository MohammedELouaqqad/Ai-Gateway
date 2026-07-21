package com.backend.Ai_Gateway.repositories;

import com.backend.Ai_Gateway.entity.UsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

public interface UsageLogRepository extends JpaRepository<UsageLog, Long>, JpaSpecificationExecutor<UsageLog> {
    List<UsageLog> findUsageLogByUserId(Long userId);

    List<UsageLog> findByUserIdAndCreatedAtBetween(Long userId, Instant start, Instant end);

    boolean existsByModel_Id(Long modelId);

    boolean existsByProvider_Id(Long providerId);
}
