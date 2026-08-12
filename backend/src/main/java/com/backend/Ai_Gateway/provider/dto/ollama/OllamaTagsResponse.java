package com.backend.Ai_Gateway.provider.dto.ollama;

import java.util.List;

/** Réponse de {@code GET /api/tags} — modèles déjà "pull"és sur ce serveur Ollama. */
public record OllamaTagsResponse(List<OllamaModelTag> models) {}
