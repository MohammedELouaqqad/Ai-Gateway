package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.services.ApiKeyService;
import com.backend.Ai_Gateway.services.UserService;
import com.backend.Ai_Gateway.services.dto.ApiKeyCreatedView;
import com.backend.Ai_Gateway.services.dto.ApiKeyView;
import com.backend.Ai_Gateway.services.dto.CreateApiKeyRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Self-service API key lifecycle — a user manages only their own keys. */
@RestController
@RequestMapping("/api/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final UserService userService;

    public ApiKeyController(ApiKeyService apiKeyService, UserService userService) {
        this.apiKeyService = apiKeyService;
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyCreatedView create(@Valid @RequestBody CreateApiKeyRequest request) {
        Long userId = userService.requireCurrentUserId();
        return apiKeyService.generate(userId, request.name(), request.expiresInDays());
    }

    @GetMapping
    public List<ApiKeyView> list() {
        Long userId = userService.requireCurrentUserId();
        return apiKeyService.list(userId);
    }

    @PatchMapping("/{id}/revoke")
    public ApiKeyView revoke(@PathVariable Long id) {
        Long userId = userService.requireCurrentUserId();
        return apiKeyService.revoke(userId, id);
    }
}
