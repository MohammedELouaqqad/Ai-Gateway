package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.UsageLog;
import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.services.UsageService;
import com.backend.Ai_Gateway.services.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final UsageService usageService;
    private final UserService userService;

    public UsageController(UsageService usageService, UserService userService) {
        this.usageService = usageService;
        this.userService = userService;
    }

    /** Compat : résumé session courante (tous les logs user). */
    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        Long userId = userService.requireCurrentUserId();
        List<UsageLog> logs = usageService.findAllMatching(userId, from, to, null, null, null, null);
        return usageService.buildSummary(logs);
    }

    /** Compat : liste complète user (préférer /logs paginé). */
    @GetMapping
    public List<UsageLog> list() {
        return usageService.getUsageByUserId(userService.requireCurrentUserId());
    }

    @GetMapping("/logs")
    public Map<String, Object> logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String q
    ) {
        Long userId = userService.requireCurrentUserId();
        Pageable pageable = toPageable(page, size, sort);
        Page<UsageLog> result = usageService.searchLogs(
                userId, from, to, model, provider, parseLocation(location), q, pageable
        );
        Map<String, Object> body = new HashMap<>();
        body.put("content", result.getContent());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return body;
    }

    @GetMapping("/by-model")
    public List<Map<String, Object>> byModel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String location
    ) {
        Long userId = userService.requireCurrentUserId();
        List<UsageLog> logs = usageService.findAllMatching(
                userId, from, to, model, provider, parseLocation(location), null
        );
        return usageService.aggregateByModel(logs);
    }

    @GetMapping("/by-user")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> byUser(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        List<UsageLog> logs = usageService.findAllMatching(null, from, to, null, null, null, null);
        return usageService.aggregateByUser(logs);
    }

    @GetMapping(value = "/export/csv", produces = "text/csv")
    public void exportCsv(
            HttpServletResponse response,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String q
    ) throws IOException {
        Long userId = userService.requireCurrentUserId();
        List<UsageLog> logs = usageService.findAllMatching(
                userId, from, to, model, provider, parseLocation(location), q
        );
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=usage-export.csv");
        PrintWriter writer = response.getWriter();
        writer.println("id,model,provider,location,tokens,cost,latencyMs,status,createdAt");
        for (UsageLog log : logs) {
            String modelName = log.getModel() != null
                    ? (log.getModel().getDisplayName() != null ? log.getModel().getDisplayName() : log.getModel().getName())
                    : "";
            String providerName = log.getModel() != null && log.getModel().getProvider() != null
                    ? log.getModel().getProvider().getName() : "";
            String loc = log.getModel() != null && log.getModel().getProvider() != null
                    && log.getModel().getProvider().getLocation() != null
                    ? log.getModel().getProvider().getLocation().name() : "";
            writer.printf(
                    "%d,%s,%s,%s,%d,%.6f,%s,%s,%s%n",
                    log.getId(),
                    csv(modelName),
                    csv(providerName),
                    csv(loc),
                    log.getTotalTokens() != null ? log.getTotalTokens() : 0,
                    log.getEstimatedCost(),
                    log.getLatencyMs() != null ? log.getLatencyMs() : "",
                    log.getStatus() != null ? log.getStatus().name() : "",
                    log.getCreatedAt() != null ? log.getCreatedAt().toString() : ""
            );
        }
        writer.flush();
    }

    private static ProviderLocation parseLocation(String location) {
        if (location == null || location.isBlank()) return null;
        String v = location.trim().toUpperCase();
        if ("LOCAL".equals(v) || "ONPREM".equals(v)) return ProviderLocation.LOCAL;
        if ("CLOUD".equals(v)) return ProviderLocation.CLOUD;
        return null;
    }

    private static Pageable toPageable(int page, int size, String sort) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        String[] parts = sort.split(",");
        String prop = parts[0].trim();
        Sort.Direction dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(safePage, safeSize, Sort.by(dir, prop));
    }

    private static String csv(String raw) {
        if (raw == null) return "";
        String escaped = raw.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
