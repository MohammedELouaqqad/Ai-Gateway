package com.backend.Ai_Gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG document ingestion (Phase 1 — upload, extraction, chunking, embedding, Qdrant storage).
 * Retrieval is NOT wired into chat yet (Phase 2). Opt-in like Redis guardrails
 * ({@code app.rag.enabled}) — off by default so a deployment without Qdrant/an embedding model
 * pulled isn't broken by this feature existing.
 */
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private boolean enabled = false;

    /** PDFs run larger than chat images — separate cap from {@code app.uploads.max-size-bytes}. */
    private long maxSizeBytes = 20_000_000L;

    /** ~4 chars/token approximation, same convention as {@code GuardrailsProperties.Estimate}. */
    private int chunkChars = 2000;
    private int chunkOverlapChars = 300;

    /** Anti-bomb: extracted text is truncated past this, never held/embedded unbounded. */
    private int maxExtractedChars = 2_000_000;

    /** Bounds how long the ingestion job waits on PDFBox for one document before giving up and
     *  marking it FAILED — see {@code PdfTextExtractor} javadoc for what this does and doesn't
     *  protect against. */
    private long extractionTimeoutMs = 30_000L;

    /** Must match the actual output dimension of whatever model {@code embedding-server}
     *  (docker-compose.yml) was launched with — nomic-embed-text-v1.5 is 768. Wrong value here
     *  fails Qdrant collection creation loudly, not silently. TEI serves one fixed model per
     *  instance (picked at its own startup, not sent per-request), so there is no
     *  "embeddingModel" property here to keep in sync — this dimension is the only thing that
     *  has to match. */
    private int embeddingDimension = 768;

    /** Chunks retrieved per chat message when the conversation has RAG documents attached. */
    private int retrievalTopK = 5;

    private RateLimit rateLimit = new RateLimit();
    private Qdrant qdrant = new Qdrant();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public int getChunkChars() {
        return chunkChars;
    }

    public void setChunkChars(int chunkChars) {
        this.chunkChars = chunkChars;
    }

    public int getChunkOverlapChars() {
        return chunkOverlapChars;
    }

    public void setChunkOverlapChars(int chunkOverlapChars) {
        this.chunkOverlapChars = chunkOverlapChars;
    }

    public int getMaxExtractedChars() {
        return maxExtractedChars;
    }

    public void setMaxExtractedChars(int maxExtractedChars) {
        this.maxExtractedChars = maxExtractedChars;
    }

    public long getExtractionTimeoutMs() {
        return extractionTimeoutMs;
    }

    public void setExtractionTimeoutMs(long extractionTimeoutMs) {
        this.extractionTimeoutMs = extractionTimeoutMs;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(int embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public int getRetrievalTopK() {
        return retrievalTopK;
    }

    public void setRetrievalTopK(int retrievalTopK) {
        this.retrievalTopK = retrievalTopK;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Qdrant getQdrant() {
        return qdrant;
    }

    public void setQdrant(Qdrant qdrant) {
        this.qdrant = qdrant;
    }

    public static class RateLimit {
        /** Lower than image uploads (app.uploads) — PDF ingestion is far more expensive
         *  (extraction + N embedding calls per document). */
        private int uploadsPerHour = 10;

        public int getUploadsPerHour() {
            return uploadsPerHour;
        }

        public void setUploadsPerHour(int uploadsPerHour) {
            this.uploadsPerHour = uploadsPerHour;
        }
    }

    public static class Qdrant {
        private String host = "qdrant";
        private int port = 6333;
        private String collection = "rag_chunks";
        /** Blank = no {@code api-key} header sent — fine for a network-isolated Qdrant (not
         *  published to the host, see docker-compose.yml), matches Ollama's own optional-auth
         *  posture for a same-network service. */
        private String apiKey = "";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
