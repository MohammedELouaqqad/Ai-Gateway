package com.backend.Ai_Gateway.services.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Embeddings for RAG chunking/retrieval — Ollama only for v1 (deliberate: keeps the embedding
 * path on the same local, sovereign infrastructure as the rest of this gateway's "LOCAL"
 * providers, and the seed already expects {@code nomic-embed-text}/{@code all-minilm} to be
 * pulled). Not part of the {@code LLMProvider} abstraction — embeddings aren't a chat
 * capability, forcing it into that interface would be a worse fit than a small dedicated class.
 */
@Component
public class OllamaEmbeddingService {

    private final RestClient.Builder restClientBuilder;
    private final String baseUrl;

    public OllamaEmbeddingService(
            RestClient.Builder restClientBuilder,
            @Value("${app.ollama.base-url:http://localhost:11434}") String baseUrl
    ) {
        this.restClientBuilder = restClientBuilder;
        this.baseUrl = baseUrl;
    }

    /** Blocking — only ever called from the ingestion job's own scheduled thread, never a
     *  request thread. */
    public List<Float> embed(String model, String text) {
        RestClient client = restClientBuilder.clone().baseUrl(baseUrl).build();
        OllamaEmbeddingResponse response = client.post()
                .uri("/api/embeddings")
                .body(Map.of("model", model, "prompt", text))
                .retrieve()
                .body(OllamaEmbeddingResponse.class);

        if (response == null || response.embedding() == null || response.embedding().isEmpty()) {
            throw new IllegalStateException("Empty embedding response from Ollama for model " + model);
        }
        return response.embedding();
    }

    private record OllamaEmbeddingResponse(List<Float> embedding) {}
}
