package com.backend.Ai_Gateway.provider.dto.openai;

import java.util.List;

/** Réponse de {@code GET /models} — forme standard OpenAI, partagée par vLLM/SGLang. */
public record OpenAIModelsResponse(List<OpenAIModelInfo> data) {}
