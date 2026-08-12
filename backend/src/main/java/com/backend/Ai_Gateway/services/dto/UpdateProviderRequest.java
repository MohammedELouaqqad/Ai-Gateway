package com.backend.Ai_Gateway.services.dto;

import com.backend.Ai_Gateway.enums.ProviderLocation;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code apiKey}: null or blank leaves the stored key untouched — deliberately NOT a way to
 * clear it (a blank password-input field is the default state of "didn't touch this field",
 * and the key is never re-displayed once set, so a clear-on-blank rule would make silently
 * wiping a working key the easy/default outcome instead of an explicit one). No clear-the-key
 * affordance exists yet — add one later (e.g. a dedicated button) if actually needed.
 * <p>
 * {@code type} deliberately absent — changing a Provider's type after creation would silently
 * reinterpret every attached Model's {@code name} under a different API contract (an Ollama tag
 * like {@code llama3.2:1b} means nothing to OpenAI). Not a field to expose generically; retype
 * means delete + recreate. {@code enabled} is absent too — {@code PATCH /{id}/status} owns it
 * (its own business rule: cascades to disable every attached Model, own audit action).
 */
public record UpdateProviderRequest(
        @NotBlank String name,
        String baseUrl,
        ProviderLocation location,
        Integer priority,
        String apiKey
) {}
