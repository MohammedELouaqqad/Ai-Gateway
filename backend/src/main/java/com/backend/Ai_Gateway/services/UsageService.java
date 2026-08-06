package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.UsageLog;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.enums.RequestStatus;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.UsageLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UsageService {

    private final UserRepository userRepository;
    private final ModelRepository modelRepository;
    private final UsageLogRepository usageLogRepository;
    private final ModelPricingService modelPricingService;

    public UsageService(
            UserRepository userRepository,
            ModelRepository modelRepository,
            UsageLogRepository usageLogRepository,
            ModelPricingService modelPricingService
    ) {
        this.userRepository = userRepository;
        this.modelRepository = modelRepository;
        this.usageLogRepository = usageLogRepository;
        this.modelPricingService = modelPricingService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logChatUsage(UsageLogEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("UsageLogEntry is required");
        }
        User user = userRepository.findById(entry.userId())
                .orElseThrow(() -> new RessourceNotFoundException("User not found"));
        Model model = modelRepository.findById(entry.modelId())
                .orElseThrow(() -> new RessourceNotFoundException("Model not found"));

        int prompt = entry.promptTokens() != null ? entry.promptTokens() : 0;
        int completion = entry.completionTokens() != null ? entry.completionTokens() : 0;

        UsageLog usageLog = new UsageLog();
        usageLog.setUser(user);
        usageLog.setModel(model);
        usageLog.setProvider(model.getProvider());
        usageLog.setPromptTokens(prompt);
        usageLog.setCompletionTokens(completion);
        usageLog.setTotalTokens(prompt + completion);
        usageLog.setLatencyMs(entry.latencyMs());
        usageLog.setStatus(entry.status());
        usageLog.setCreatedAt(Instant.now());
        usageLog.setEstimatedCost(calculateCost(prompt, completion, model.getName()));

        usageLogRepository.save(usageLog);
    }

    public int getTotalTokensByUserId(Long userId) {
        return usageLogRepository.findUsageLogByUserId(userId).stream()
                .mapToInt(UsageLog::getTotalTokens)
                .sum();
    }

    public double getTotalCostByUserId(Long userId) {
        return usageLogRepository.findUsageLogByUserId(userId).stream()
                .mapToDouble(UsageLog::getEstimatedCost)
                .sum();
    }

    public List<UsageLog> getUsageByPeriod(Long userId, Instant start, Instant end) {
        return usageLogRepository.findByUserIdAndCreatedAtBetween(userId, start, end);
    }

    public double calculateCost(int promptTokens, int completionTokens, String modelName) {
        return modelPricingService.calculateCostEur(promptTokens, completionTokens, modelName);
    }

    public List<UsageLog> getUsageByUserId(Long userId) {
        return usageLogRepository.findUsageLogByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Page<UsageLog> searchLogs(
            Long userId,
            Instant from,
            Instant to,
            String model,
            String provider,
            ProviderLocation location,
            String q,
            Pageable pageable
    ) {
        Specification<UsageLog> spec = UsageLogSpecs.filter(userId, from, to, model, provider, location, q);
        Page<UsageLog> page = usageLogRepository.findAll(spec, pageable);
        page.forEach(this::touchAssociations);
        return page;
    }

    @Transactional(readOnly = true)
    public List<UsageLog> findAllMatching(
            Long userId,
            Instant from,
            Instant to,
            String model,
            String provider,
            ProviderLocation location,
            String q
    ) {
        List<UsageLog> logs = usageLogRepository.findAll(UsageLogSpecs.filter(userId, from, to, model, provider, location, q));
        logs.forEach(this::touchAssociations);
        return logs;
    }

    private void touchAssociations(UsageLog log) {
        if (log.getModel() != null) {
            log.getModel().getName();
            if (log.getModel().getProvider() != null) {
                log.getModel().getProvider().getName();
            }
        }
        if (log.getUser() != null) {
            log.getUser().getUsername();
        }
    }

    public Map<String, Object> buildSummary(List<UsageLog> logs) {
        int tokens = 0;
        double cost = 0;
        int errors = 0;
        long latencySum = 0;
        int latencyCount = 0;
        for (UsageLog log : logs) {
            tokens += log.getTotalTokens() != null ? log.getTotalTokens() : 0;
            cost += log.getEstimatedCost();
            if (log.getStatus() == RequestStatus.ERROR) {
                errors++;
            }
            if (log.getLatencyMs() != null) {
                latencySum += log.getLatencyMs();
                latencyCount++;
            }
        }
        int requests = logs.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTokens", tokens);
        result.put("totalCost", cost);
        result.put("requestCount", requests);
        result.put("errorCount", errors);
        result.put("errorRate", requests == 0 ? null : (double) errors / requests);
        result.put("avgLatencyMs", latencyCount == 0 ? null : Math.round((double) latencySum / latencyCount));
        return result;
    }

    public List<Map<String, Object>> aggregateByModel(List<UsageLog> logs) {
        Map<String, Agg> map = new HashMap<>();
        for (UsageLog log : logs) {
            String modelName = log.getModel() != null
                    ? (log.getModel().getDisplayName() != null ? log.getModel().getDisplayName() : log.getModel().getName())
                    : "—";
            String providerName = log.getModel() != null && log.getModel().getProvider() != null
                    ? log.getModel().getProvider().getName()
                    : "—";
            String location = log.getModel() != null && log.getModel().getProvider() != null
                    && log.getModel().getProvider().getLocation() != null
                    ? log.getModel().getProvider().getLocation().name()
                    : "LOCAL";
            String key = providerName + "|" + (log.getModel() != null ? log.getModel().getName() : modelName);
            Agg agg = map.computeIfAbsent(key, k -> new Agg(modelName, providerName, location));
            agg.requests++;
            agg.tokens += log.getTotalTokens() != null ? log.getTotalTokens() : 0;
            agg.cost += log.getEstimatedCost();
        }
        long totalTokens = map.values().stream().mapToLong(a -> a.tokens).sum();
        if (totalTokens == 0) totalTokens = 1;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Agg a : map.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("model", a.model);
            row.put("provider", a.provider);
            row.put("type", "LOCAL".equals(a.location) ? "onprem" : "cloud");
            row.put("location", a.location);
            row.put("requests", a.requests);
            row.put("tokens", a.tokens);
            row.put("cost", a.cost);
            row.put("sharePercent", Math.round((a.tokens * 100.0) / totalTokens));
            rows.add(row);
        }
        rows.sort(Comparator.comparingLong((Map<String, Object> r) -> ((Number) r.get("tokens")).longValue()).reversed());
        return rows;
    }

    public List<Map<String, Object>> aggregateByUser(List<UsageLog> logs) {
        Map<String, UserAgg> map = new HashMap<>();
        for (UsageLog log : logs) {
            User u = log.getUser();
            String key = u != null ? String.valueOf(u.getId()) : "—";
            String label = u == null ? "—"
                    : (u.getUsername() != null && !u.getUsername().isBlank()
                    ? u.getUsername()
                    : (u.getEmail() != null ? u.getEmail() : "#" + u.getId()));
            UserAgg agg = map.computeIfAbsent(key, k -> new UserAgg(label));
            agg.requests++;
            agg.tokens += log.getTotalTokens() != null ? log.getTotalTokens() : 0;
            agg.cost += log.getEstimatedCost();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserAgg a : map.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user", a.user);
            row.put("requests", a.requests);
            row.put("tokens", a.tokens);
            row.put("cost", a.cost);
            rows.add(row);
        }
        rows.sort(Comparator.comparingDouble((Map<String, Object> r) -> ((Number) r.get("cost")).doubleValue()).reversed());
        return rows;
    }

    private static final class Agg {
        final String model;
        final String provider;
        final String location;
        int requests;
        long tokens;
        double cost;

        Agg(String model, String provider, String location) {
            this.model = model;
            this.provider = provider;
            this.location = location;
        }
    }

    private static final class UserAgg {
        final String user;
        int requests;
        long tokens;
        double cost;

        UserAgg(String user) {
            this.user = user;
        }
    }
}
