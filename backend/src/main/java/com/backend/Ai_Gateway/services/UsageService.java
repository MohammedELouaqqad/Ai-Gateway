package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.ModelPricing;
import com.backend.Ai_Gateway.enums.RequestStatus;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.UsageLog;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class UsageService {

    private final UserRepository userRepository;
    private final ModelRepository modelRepository;
    private final UsageLogRepository usageLogRepository;

    public UsageService(
            UserRepository userRepository,
            ModelRepository modelRepository,
            UsageLogRepository usageLogRepository
    ) {
        this.userRepository = userRepository;
        this.modelRepository = modelRepository;
        this.usageLogRepository = usageLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logChatUsage(
            Long userId,
            Long modelId,
            Integer promptTokens,
            Integer completionTokens,
            long latencyMs,
            RequestStatus status
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RessourceNotFoundException("User not found"));
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new RessourceNotFoundException("Model not found"));

        int prompt = promptTokens != null ? promptTokens : 0;
        int completion = completionTokens != null ? completionTokens : 0;

        UsageLog usageLog = new UsageLog();
        usageLog.setUser(user);
        usageLog.setModel(model);
        usageLog.setProvider(model.getProvider());
        usageLog.setPromptTokens(prompt);
        usageLog.setCompletionTokens(completion);
        usageLog.setTotalTokens(prompt + completion);
        usageLog.setLatencyMs(latencyMs);
        usageLog.setStatus(status);
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
        ModelPricing.PricePerMillion pricing = ModelPricing.forModel(modelName);
        return (promptTokens / 1_000_000.0) * pricing.inputUsd()
                + (completionTokens / 1_000_000.0) * pricing.outputUsd();
    }

    public List<UsageLog> getUsageByUserId(Long userId) {
        return usageLogRepository.findUsageLogByUserId(userId);
    }
}
