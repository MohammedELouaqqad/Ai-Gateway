package com.backend.Ai_Gateway.provider.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Un modèle listé par {@code GET /models} — {@code id} est le nom exact à réutiliser tel quel. */
public record OpenAIModelInfo(String id, String object, @JsonProperty("owned_by") String ownedBy) {}
