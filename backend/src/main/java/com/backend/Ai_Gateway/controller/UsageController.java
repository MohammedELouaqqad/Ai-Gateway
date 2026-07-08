package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.UsageLog;
import com.backend.Ai_Gateway.services.UsageService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final UsageService usageService;

    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Long userId = 1L;
        List<UsageLog> logs = usageService.getUsageByUserId(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("totalTokens", usageService.getTotalTokensByUserId(userId));
        result.put("totalCost", usageService.getTotalCostByUserId(userId));
        result.put("requestCount", logs.size());
        return result;
    }

    @GetMapping
    public List<UsageLog> list() {
        Long userId = 1L;
        return usageService.getUsageByUserId(userId);
    }
}
