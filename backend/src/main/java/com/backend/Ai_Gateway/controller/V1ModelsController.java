package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.controller.dto.v1.V1ModelListResponse;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.services.ModelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/models")
public class V1ModelsController {

    private final ModelService modelService;

    public V1ModelsController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public V1ModelListResponse list() {
        var models = modelService.getAllModels().stream()
                .filter(this::isUsableNow)
                .map(this::toV1Model)
                .toList();
        return V1ModelListResponse.of(models);
    }

    /** Only list models a client could actually call right now — enabled model, enabled provider, adapter written. */
    private boolean isUsableNow(Model model) {
        return model.isEnabled()
                && model.getProvider() != null
                && model.getProvider().isEnabled()
                && model.isAvailable();
    }

    private V1ModelListResponse.V1Model toV1Model(Model model) {
        return new V1ModelListResponse.V1Model(
                model.getName(),
                "model",
                model.getCreatedAt() != null ? model.getCreatedAt().getEpochSecond() : 0,
                model.getProvider().getName()
        );
    }
}
