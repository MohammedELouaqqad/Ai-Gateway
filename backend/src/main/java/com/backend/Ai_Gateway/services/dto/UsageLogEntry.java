package com.backend.Ai_Gateway.services.dto;

import com.backend.Ai_Gateway.enums.RequestStatus;

/**
 * Named fields for {@link com.backend.Ai_Gateway.services.UsageService#logChatUsage(UsageLogEntry)}
 * so call sites stay self-documenting (no positional arg lists).
 */
public record UsageLogEntry(
        Long userId,
        Long modelId,
        Integer promptTokens,
        Integer completionTokens,
        long latencyMs,
        RequestStatus status
) {
    public static UsageLogEntry error(Long userId, Long modelId, long latencyMs) {
        return new UsageLogEntry(userId, modelId, 0, 0, latencyMs, RequestStatus.ERROR);
    }

    public static UsageLogEntry success(
            Long userId,
            Long modelId,
            Integer promptTokens,
            Integer completionTokens,
            long latencyMs
    ) {
        return new UsageLogEntry(userId, modelId, promptTokens, completionTokens, latencyMs, RequestStatus.SUCCESS);
    }
}
