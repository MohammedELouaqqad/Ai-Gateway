package com.backend.Ai_Gateway.services.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Embeddings for RAG chunking/retrieval — HuggingFace Text Embeddings Inference (TEI), not
 * Ollama: an Ollama runtime image weighs ~3.2 GB just for the server itself (bundled multi-
 * backend GPU support, unneeded here) against a ~274 MB embedding model — TEI is purpose-built
 * for embedding, a meaningfully lighter image, and needs no separate model-pull step (it
 * downloads its configured model at its own startup).
 * <p>
 * Deliberately its own property ({@code app.rag.embedding-base-url}), NOT
 * {@code app.ollama.base-url} (that one is the chat {@code OllamaProvider}'s host Ollama) — RAG
 * embeddings must never be coupled to whatever the user picked as their chat provider. In the
 * Docker stack this points at the dedicated {@code embedding-server} service
 * (docker-compose.yml), so RAG works regardless of whether the user has Ollama, SGLang, or
 * anything else installed for chat.
 * <p>
 * TEI serves exactly one model per running instance (fixed at container startup via
 * {@code --model-id}), so unlike Ollama's {@code /api/embeddings} the request here carries no
 * per-call model name — {@code app.rag.embedding-model} exists purely to document which model
 * the configured {@code embedding-server} was launched with, it is never sent over the wire.
 */
@Component
public class TeiEmbeddingService {

    private final RestClient.Builder restClientBuilder;
    private final String baseUrl;

    public TeiEmbeddingService(
            RestClient.Builder restClientBuilder,
            @Value("${app.rag.embedding-base-url}") String baseUrl
    ) {
        this.restClientBuilder = restClientBuilder;
        this.baseUrl = baseUrl;
    }

    /** Blocking — only ever called from the ingestion job's own scheduled thread or the request
     *  thread doing RAG retrieval (see RagRetrievalService javadoc for why blocking is fine
     *  there), never the Reactor Netty event loop. */
    public List<Float> embed(String text) {
        // TEI's native route: POST /embed {"inputs": "..."} -> [[float, float, ...]] (one row
        // per input — a single string in means a single-element outer array back).
        List<List<Float>> response = restClientBuilder.clone().baseUrl(baseUrl).build()
                .post()
                .uri("/embed")
                .body(Map.of("inputs", text))
                .retrieve()
                .body(new ParameterizedTypeReference<List<List<Float>>>() {});

        if (response == null || response.isEmpty() || response.get(0) == null || response.get(0).isEmpty()) {
            throw new IllegalStateException("Empty embedding response from TEI");
        }
        return response.get(0);
    }
}
