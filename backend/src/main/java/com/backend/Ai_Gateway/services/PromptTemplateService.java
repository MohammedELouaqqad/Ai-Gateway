package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.ContentLimits;
import com.backend.Ai_Gateway.entity.PromptTemplate;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.exceptions.RessourceAlreadyExistsException;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.repositories.PromptTemplateRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.CreatePromptTemplateRequest;
import com.backend.Ai_Gateway.services.dto.UpdatePromptTemplateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PromptTemplateService {

    private final PromptTemplateRepository promptTemplateRepository;
    private final UserRepository userRepository;

    public PromptTemplateService(
            PromptTemplateRepository promptTemplateRepository,
            UserRepository userRepository
    ) {
        this.promptTemplateRepository = promptTemplateRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<PromptTemplate> listByUser(Long userId) {
        requireUser(userId);
        return promptTemplateRepository.findByUser_IdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public PromptTemplate getById(Long userId, Long id) {
        PromptTemplate template = promptTemplateRepository.findById(id)
                .orElseThrow(() -> new RessourceNotFoundException("Prompt template not found with id " + id));
        assertOwnership(userId, template);
        return template;
    }

    @Transactional
    public PromptTemplate create(Long userId, CreatePromptTemplateRequest request) {
        User user = requireUser(userId);
        String name = normalizeRequired(request.name(), "name");
        String content = normalizeRequired(request.content(), "content");
        String description = normalizeOptional(request.description());

        if (promptTemplateRepository.existsByUser_IdAndNameIgnoreCase(userId, name)) {
            throw new RessourceAlreadyExistsException("Prompt template already exists: " + name);
        }

        PromptTemplate template = new PromptTemplate();
        template.setUser(user);
        template.setName(name);
        template.setDescription(description);
        template.setContent(content);
        template.setCreatedAt(Instant.now());
        return promptTemplateRepository.save(template);
    }

    @Transactional
    public PromptTemplate update(Long userId, Long id, UpdatePromptTemplateRequest request) {
        PromptTemplate template = getById(userId, id);
        String name = normalizeRequired(request.name(), "name");
        String content = normalizeRequired(request.content(), "content");
        String description = normalizeOptional(request.description());

        promptTemplateRepository.findByUser_IdAndNameIgnoreCase(userId, name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new RessourceAlreadyExistsException("Prompt template already exists: " + name);
                });

        template.setName(name);
        template.setDescription(description);
        template.setContent(content);
        return promptTemplateRepository.save(template);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        PromptTemplate template = getById(userId, id);
        promptTemplateRepository.delete(template);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RessourceNotFoundException("User not found"));
    }

    private void assertOwnership(Long userId, PromptTemplate template) {
        if (template.getUser() == null || !template.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Vous n'êtes pas propriétaire de ce template");
        }
    }

    private String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + ": must not be blank");
        }
        String trimmed = value.trim();
        int max = switch (field) {
            case "name" -> ContentLimits.TEMPLATE_NAME_MAX;
            case "content" -> ContentLimits.TEMPLATE_CONTENT_MAX;
            default -> Integer.MAX_VALUE;
        };
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + ": max " + max + " characters");
        }
        return trimmed;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > ContentLimits.TEMPLATE_DESCRIPTION_MAX) {
            throw new IllegalArgumentException(
                    "description: max " + ContentLimits.TEMPLATE_DESCRIPTION_MAX + " characters"
            );
        }
        return trimmed;
    }
}
