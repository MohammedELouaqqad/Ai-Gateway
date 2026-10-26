package com.backend.Ai_Gateway.services.rag;

import com.backend.Ai_Gateway.config.RagProperties;
import com.backend.Ai_Gateway.services.rag.dto.QdrantPoint;
import com.backend.Ai_Gateway.services.rag.dto.RagChunkHit;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Talks to Qdrant over its plain REST API — deliberately not the official Java client (gRPC,
 * pulls netty/protobuf) to stay consistent with how this gateway already talks to every LLM
 * provider (WebClient/RestClient, no vendor SDKs).
 * <p>
 * {@link #ensureCollection}/{@link #upsertPoints}/{@link #deleteByDocumentId} run on the
 * ingestion job's own thread ({@code RagIngestionJob}, `@Scheduled`) — never the HTTP request
 * thread. {@link #search}, unlike those, IS called from a request path
 * ({@code RagRetrievalService}, from {@code ChatService#prepareChat}) — but that's still the
 * plain Tomcat servlet thread (blocking-by-design), not the Reactor Netty event loop the
 * streaming chat path uses once the LLM call itself starts. Blocking here is fine; it would not
 * be fine inside a `Flux` subscribe callback.
 */
@Component
public class QdrantClient {

    private final RestClient.Builder restClientBuilder;
    private final RagProperties ragProperties;

    public QdrantClient(RestClient.Builder restClientBuilder, RagProperties ragProperties) {
        this.restClientBuilder = restClientBuilder;
        this.ragProperties = ragProperties;
    }

    /** Idempotent — creates the collection only if it doesn't exist yet. */
    public void ensureCollection() {
        String collection = ragProperties.getQdrant().getCollection();
        try {
            client().get().uri("/collections/{name}", collection).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 404) {
                throw e;
            }
            client().put().uri("/collections/{name}", collection)
                    .body(Map.of("vectors", Map.of(
                            "size", ragProperties.getEmbeddingDimension(),
                            "distance", "Cosine"
                    )))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    public void upsertPoints(List<QdrantPoint> points) {
        if (points.isEmpty()) {
            return;
        }
        client().put()
                .uri("/collections/{name}/points?wait=true", ragProperties.getQdrant().getCollection())
                .body(Map.of("points", points))
                .retrieve()
                .toBodilessEntity();
    }

    /** Called before re-ingesting a document (avoids stale chunks lingering if the new version
     *  has fewer chunks than the old one) and on document delete. */
    public void deleteByDocumentId(Long documentId) {
        client().post()
                .uri("/collections/{name}/points/delete?wait=true", ragProperties.getQdrant().getCollection())
                .body(Map.of("filter", Map.of(
                        "must", List.of(Map.of(
                                "key", "documentId",
                                "match", Map.of("value", documentId)
                        ))
                )))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Top-{@code topK} chunks nearest {@code queryVector}, restricted to {@code documentIds}
     * AND {@code ownerId} — the owner filter is defense-in-depth (callers already scope
     * {@code documentIds} to documents the caller owns via
     * {@code RagDocumentService}/{@code Conversation.ragDocuments}), not the only IDOR guard.
     * Returns an empty list — never throws — on any Qdrant error; see
     * {@code RagRetrievalService} for why retrieval degrades gracefully instead of failing the
     * whole chat request.
     */
    public List<RagChunkHit> search(List<Float> queryVector, int topK, List<Long> documentIds, Long ownerId) {
        if (documentIds.isEmpty()) {
            return List.of();
        }
        QdrantSearchResponse response = client().post()
                .uri("/collections/{name}/points/search", ragProperties.getQdrant().getCollection())
                .body(Map.of(
                        "vector", queryVector,
                        "limit", topK,
                        "filter", Map.of("must", List.of(
                                Map.of("key", "documentId", "match", Map.of("any", documentIds)),
                                Map.of("key", "ownerId", "match", Map.of("value", ownerId))
                        )),
                        "with_payload", true
                ))
                .retrieve()
                .body(QdrantSearchResponse.class);

        if (response == null || response.result() == null) {
            return List.of();
        }
        List<RagChunkHit> hits = new ArrayList<>();
        for (QdrantSearchResult r : response.result()) {
            Map<String, Object> payload = r.payload();
            if (payload == null || payload.get("text") == null) {
                continue;
            }
            hits.add(new RagChunkHit(
                    toLong(payload.get("documentId")),
                    String.valueOf(payload.getOrDefault("filename", "document.pdf")),
                    toInt(payload.get("chunkIndex")),
                    String.valueOf(payload.get("text")),
                    r.score()
            ));
        }
        return hits;
    }

    private record QdrantSearchResponse(List<QdrantSearchResult> result) {}

    private record QdrantSearchResult(String id, double score, Map<String, Object> payload) {}

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static int toInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private RestClient client() {
        RagProperties.Qdrant cfg = ragProperties.getQdrant();
        return restClientBuilder.clone()
                .baseUrl("http://" + cfg.getHost() + ":" + cfg.getPort())
                .defaultHeaders(headers -> {
                    if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
                        headers.set("api-key", cfg.getApiKey());
                    }
                })
                .build();
    }
}
