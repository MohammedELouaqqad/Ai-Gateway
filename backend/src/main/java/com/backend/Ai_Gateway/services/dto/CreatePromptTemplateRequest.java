package com.backend.Ai_Gateway.services.dto;

import com.backend.Ai_Gateway.config.ContentLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePromptTemplateRequest(
        @NotBlank @Size(max = ContentLimits.TEMPLATE_NAME_MAX) String name,
        @Size(max = ContentLimits.TEMPLATE_DESCRIPTION_MAX) String description,
        @NotBlank @Size(max = ContentLimits.TEMPLATE_CONTENT_MAX) String content
) {}
