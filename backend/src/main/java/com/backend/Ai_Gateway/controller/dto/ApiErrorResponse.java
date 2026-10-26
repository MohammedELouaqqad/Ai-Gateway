package com.backend.Ai_Gateway.controller.dto;

import java.time.Instant;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String reason,
        Instant resetsAt
) {
    public ApiErrorResponse(Instant timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null, null);
    }
}
