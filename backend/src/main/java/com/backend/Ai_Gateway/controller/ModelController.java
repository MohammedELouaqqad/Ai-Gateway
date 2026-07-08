package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.services.ModelService;
import com.backend.Ai_Gateway.services.dto.CreateModelRequest;
import com.backend.Ai_Gateway.services.dto.UpdateModelRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public List<Model> list() {
        return modelService.getAllModels();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Model create(@Valid @RequestBody CreateModelRequest request) {
        return modelService.createModel(request);
    }

    @PutMapping("/{id}")
    public Model update(@PathVariable Long id, @Valid @RequestBody UpdateModelRequest request) {
        return modelService.updateModel(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        modelService.deleteModel(id);
    }

    @PatchMapping("/{id}/status")
    public Model toggleStatus(@PathVariable Long id) {
        return modelService.changeStatus(id);
    }

    @PatchMapping("/{id}/default")
    public Model setDefault(@PathVariable Long id) {
        return modelService.defaultModel(id);
    }
}
