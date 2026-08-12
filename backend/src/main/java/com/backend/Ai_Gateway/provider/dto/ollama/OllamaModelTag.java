package com.backend.Ai_Gateway.provider.dto.ollama;

/** Un modèle listé par {@code GET /api/tags} — {@code name} est le tag exact à réutiliser tel quel. */
public record OllamaModelTag(String name, String model) {}
