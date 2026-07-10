package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.PromptTemplate;
import com.backend.Ai_Gateway.services.PromptTemplateService;
import com.backend.Ai_Gateway.services.dto.CreatePromptTemplateRequest;
import com.backend.Ai_Gateway.services.dto.UpdatePromptTemplateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prompt-templates")
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    public PromptTemplateController(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @GetMapping
    public List<PromptTemplate> list() {
        Long userId = 1L;
        return promptTemplateService.listByUser(userId);
    }

    @GetMapping("/{id}")
    public PromptTemplate get(@PathVariable Long id) {
        Long userId = 1L;
        return promptTemplateService.getById(userId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromptTemplate create(@Valid @RequestBody CreatePromptTemplateRequest request) {
        Long userId = 1L;
        return promptTemplateService.create(userId, request);
    }

    @PutMapping("/{id}")
    public PromptTemplate update(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePromptTemplateRequest request
    ) {
        Long userId = 1L;
        return promptTemplateService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long userId = 1L;
        promptTemplateService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
