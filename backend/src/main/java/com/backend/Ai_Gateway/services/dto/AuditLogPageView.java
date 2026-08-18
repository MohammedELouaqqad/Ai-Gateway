package com.backend.Ai_Gateway.services.dto;

import java.util.List;

public record AuditLogPageView(
        List<AuditLogEntryView> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
