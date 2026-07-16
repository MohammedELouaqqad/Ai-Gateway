package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.PromptTemplate;
import com.backend.Ai_Gateway.services.PromptTemplateService;
import com.backend.Ai_Gateway.services.UserService;
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
    private final UserService userService;

    public PromptTemplateController(PromptTemplateService promptTemplateService, UserService userService) {
        this.promptTemplateService = promptTemplateService;
        this.userService = userService;
    }

    @GetMapping
    public List<PromptTemplate> list() {
        return promptTemplateService.listByUser(userService.requireCurrentUserId());
    }

    @GetMapping("/{id}")
    public PromptTemplate get(@PathVariable Long id) {
        return promptTemplateService.getById(userService.requireCurrentUserId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromptTemplate create(@Valid @RequestBody CreatePromptTemplateRequest request) {
        return promptTemplateService.create(userService.requireCurrentUserId(), request);
    }

    @PutMapping("/{id}")
    public PromptTemplate update(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePromptTemplateRequest request
    ) {
        return promptTemplateService.update(userService.requireCurrentUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        promptTemplateService.delete(userService.requireCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
