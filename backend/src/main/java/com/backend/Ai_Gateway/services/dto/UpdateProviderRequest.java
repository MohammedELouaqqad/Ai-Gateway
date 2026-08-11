package com.backend.Ai_Gateway.services.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code apiKey}: null or blank leaves the stored key untouched — deliberately NOT a way to
 * clear it (a blank password-input field is the default state of "didn't touch this field",
 * and the key is never re-displayed once set, so a clear-on-blank rule would make silently
 * wiping a working key the easy/default outcome instead of an explicit one). No clear-the-key
 * affordance exists yet — add one later (e.g. a dedicated button) if actually needed.
 */
public record UpdateProviderRequest(
        @NotBlank String name,
        String baseUrl,
        Integer priority,
        String apiKey
) {}
