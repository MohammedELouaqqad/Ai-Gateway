package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.enums.AuditAction;
import com.backend.Ai_Gateway.services.AuditDiffs;
import com.backend.Ai_Gateway.services.AuditService;
import com.backend.Ai_Gateway.services.ModelService;
import com.backend.Ai_Gateway.services.dto.CreateModelRequest;
import com.backend.Ai_Gateway.services.dto.UpdateModelRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final ModelService modelService;
    private final AuditService auditService;

    public ModelController(ModelService modelService, AuditService auditService) {
        this.modelService = modelService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<Model> list() {
        return modelService.getAllModels();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Model create(@Valid @RequestBody CreateModelRequest request) {
        Model created = modelService.createModel(request);
        auditService.record(
                AuditAction.MODEL_CREATE, "MODEL", created.getId(), created.getDisplayName(),
                Map.of("name", created.getName(), "enabled", created.isEnabled(), "isDefault", created.isDefault())
        );
        return created;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Model update(@PathVariable Long id, @Valid @RequestBody UpdateModelRequest request) {
        // Snapshot scalars before the call — see ProviderController#update for why not the entity itself.
        Model existing = modelService.getById(id);
        Map<String, Object> before = modelDiffFields(existing);

        Model updated = modelService.updateModel(id, request);

        Map<String, Object> details = AuditDiffs.diff(before, modelDiffFields(updated));
        auditService.record(AuditAction.MODEL_UPDATE, "MODEL", id, updated.getDisplayName(), details);
        return updated;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        Model existing = modelService.getById(id);
        String label = existing.getDisplayName();
        modelService.deleteModel(id);
        auditService.record(AuditAction.MODEL_DELETE, "MODEL", id, label, null);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Model toggleStatus(@PathVariable Long id) {
        Model updated = modelService.changeStatus(id);
        auditService.record(
                updated.isEnabled() ? AuditAction.MODEL_ENABLE : AuditAction.MODEL_DISABLE,
                "MODEL", updated.getId(), updated.getDisplayName(), null
        );
        return updated;
    }

    @PatchMapping("/{id}/default")
    @PreAuthorize("hasRole('ADMIN')")
    public Model setDefault(@PathVariable Long id) {
        Model updated = modelService.defaultModel(id);
        auditService.record(AuditAction.MODEL_SET_DEFAULT, "MODEL", updated.getId(), updated.getDisplayName(), null);
        return updated;
    }

    private static Map<String, Object> modelDiffFields(Model model) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("displayName", model.getDisplayName());
        fields.put("name", model.getName());
        fields.put("providerId", model.getProvider() != null ? model.getProvider().getId() : "null");
        return fields;
    }
}
