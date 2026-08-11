package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.AuditAction;
import com.backend.Ai_Gateway.services.AuditDiffs;
import com.backend.Ai_Gateway.services.AuditService;
import com.backend.Ai_Gateway.services.ProviderService;
import com.backend.Ai_Gateway.services.dto.CreateProviderRequest;
import com.backend.Ai_Gateway.services.dto.UpdateProviderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderService providerService;
    private final AuditService auditService;

    public ProviderController(ProviderService providerService, AuditService auditService) {
        this.providerService = providerService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<Provider> list() {
        return providerService.getAllProviders();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Provider create(@Valid @RequestBody CreateProviderRequest request) {
        Provider created = providerService.createProvider(request);
        auditService.record(
                AuditAction.PROVIDER_CREATE, "PROVIDER", created.getId(), created.getName(),
                Map.of(
                        "type", created.getType(),
                        "location", created.getLocation() != null ? created.getLocation() : "null",
                        "apiKeyPresent", hasApiKey(created)
                )
        );
        return created;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Provider update(@PathVariable Long id, @Valid @RequestBody UpdateProviderRequest request) {
        // Snapshot scalar fields BEFORE the update — never hold the managed entity itself across
        // the call and re-read it after, its fields could reflect the post-update state by then.
        Provider existing = providerService.getProviderById(id);
        Map<String, Object> before = providerDiffFields(existing);

        Provider updated = providerService.updateProvider(id, request);

        Map<String, Object> details = new LinkedHashMap<>(AuditDiffs.diff(before, providerDiffFields(updated)));
        // Same "was a real value actually supplied" predicate ProviderService uses to decide
        // whether to touch the stored key — presence alone would miss "replaced an existing
        // key with a different one" (both before/after are "present"). Never the value itself.
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            details.put("apiKeyChanged", true);
        }
        auditService.record(AuditAction.PROVIDER_UPDATE, "PROVIDER", id, updated.getName(), details);
        return updated;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        Provider existing = providerService.getProviderById(id);
        String name = existing.getName();
        providerService.deleteProvider(id);
        auditService.record(AuditAction.PROVIDER_DELETE, "PROVIDER", id, name, null);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Provider toggleStatus(@PathVariable Long id) {
        Provider updated = providerService.changeStatus(id);
        auditService.record(
                updated.isEnabled() ? AuditAction.PROVIDER_ENABLE : AuditAction.PROVIDER_DISABLE,
                "PROVIDER", updated.getId(), updated.getName(), null
        );
        return updated;
    }

    /** Scalar-only snapshot for diffing — deliberately excludes {@code apiKey} (see {@link #update}). */
    private static Map<String, Object> providerDiffFields(Provider provider) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", provider.getName());
        fields.put("baseUrl", provider.getBaseUrl() != null ? provider.getBaseUrl() : "null");
        fields.put("priority", provider.getPriority() != null ? provider.getPriority() : "null");
        return fields;
    }

    private static boolean hasApiKey(Provider provider) {
        return provider.getApiKey() != null && !provider.getApiKey().isBlank();
    }
}
