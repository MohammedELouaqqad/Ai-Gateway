package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.RagProperties;
import com.backend.Ai_Gateway.entity.RagDocument;
import com.backend.Ai_Gateway.enums.RagDocumentStatus;
import com.backend.Ai_Gateway.repositories.RagDocumentRepository;
import com.backend.Ai_Gateway.services.rag.TeiEmbeddingService;
import com.backend.Ai_Gateway.services.rag.PdfTextExtractor;
import com.backend.Ai_Gateway.services.rag.QdrantClient;
import com.backend.Ai_Gateway.services.rag.TextChunker;
import com.backend.Ai_Gateway.services.rag.dto.QdrantPoint;
import com.backend.Ai_Gateway.services.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Picks up {@code PENDING} (and orphaned {@code PROCESSING}, see that status's javadoc)
 * {@link RagDocument} rows and runs extract → chunk → embed → upsert, off the request thread.
 * <p>
 * Entity updates are split into short, separate transactions ({@link #markProcessing} /
 * {@link #markResult}) around the actual heavy lifting (PDF parsing, N embedding HTTP calls) —
 * that work deliberately runs with NO transaction open, so a slow/large document doesn't pin a
 * DB connection for the whole ingestion instead of just the two quick status writes.
 */
@Component
public class RagIngestionJob {

    private static final Logger log = LoggerFactory.getLogger(RagIngestionJob.class);
    private static final long POLL_DELAY_MS = 15_000L;

    private final RagDocumentRepository ragDocumentRepository;
    private final FileStorageService fileStorageService;
    private final PdfTextExtractor pdfTextExtractor;
    private final TeiEmbeddingService embeddingService;
    private final QdrantClient qdrantClient;
    private final RagProperties ragProperties;

    public RagIngestionJob(
            RagDocumentRepository ragDocumentRepository,
            FileStorageService fileStorageService,
            PdfTextExtractor pdfTextExtractor,
            TeiEmbeddingService embeddingService,
            QdrantClient qdrantClient,
            RagProperties ragProperties
    ) {
        this.ragDocumentRepository = ragDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.pdfTextExtractor = pdfTextExtractor;
        this.embeddingService = embeddingService;
        this.qdrantClient = qdrantClient;
        this.ragProperties = ragProperties;
    }

    @Scheduled(fixedDelay = POLL_DELAY_MS, initialDelay = 20_000L)
    public void processPending() {
        if (!ragProperties.isEnabled()) {
            return;
        }
        List<RagDocument> pending = ragDocumentRepository.findByStatusIn(
                List.of(RagDocumentStatus.PENDING, RagDocumentStatus.PROCESSING));
        if (pending.isEmpty()) {
            return;
        }

        qdrantClient.ensureCollection();

        for (RagDocument snapshot : pending) {
            Long id = snapshot.getId();
            Long ownerId = snapshot.getOwner().getId();
            String storageKey = snapshot.getStorageKey();
            String filename = snapshot.getOriginalFilename();
            markProcessing(id);
            try {
                byte[] bytes = readAllBytes(storageKey);
                String text = pdfTextExtractor.extract(
                        bytes, ragProperties.getExtractionTimeoutMs(), ragProperties.getMaxExtractedChars());
                List<String> chunks = TextChunker.chunk(
                        text, ragProperties.getChunkChars(), ragProperties.getChunkOverlapChars());

                // Clear any points from a previous partial/failed attempt before re-upserting —
                // avoids stale leftover chunks if this run produces fewer than last time.
                qdrantClient.deleteByDocumentId(id);
                qdrantClient.upsertPoints(toPoints(id, ownerId, filename, chunks));

                markResult(id, RagDocumentStatus.READY, chunks.size(), null);
                log.info("RAG ingestion: document {} ready ({} chunks)", id, chunks.size());
            } catch (Exception e) {
                log.warn("RAG ingestion failed for document {}: {}", id, e.toString());
                markResult(id, RagDocumentStatus.FAILED, 0, truncate(e.getMessage(), 500));
            }
        }
    }

    @Transactional
    void markProcessing(Long documentId) {
        ragDocumentRepository.findById(documentId).ifPresent(d -> {
            d.setStatus(RagDocumentStatus.PROCESSING);
            ragDocumentRepository.save(d);
        });
    }

    @Transactional
    void markResult(Long documentId, RagDocumentStatus status, int chunkCount, String errorMessage) {
        ragDocumentRepository.findById(documentId).ifPresent(d -> {
            d.setStatus(status);
            d.setChunkCount(chunkCount);
            d.setErrorMessage(errorMessage);
            d.setProcessedAt(Instant.now());
            ragDocumentRepository.save(d);
        });
    }

    private byte[] readAllBytes(String storageKey) throws IOException {
        try (InputStream in = fileStorageService.read(storageKey)) {
            return in.readAllBytes();
        }
    }

    private List<QdrantPoint> toPoints(Long documentId, Long ownerId, String filename, List<String> chunks) {
        List<QdrantPoint> points = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            List<Float> vector = embeddingService.embed(chunkText);
            String pointId = UUID.nameUUIDFromBytes((documentId + ":" + i).getBytes(StandardCharsets.UTF_8)).toString();
            points.add(new QdrantPoint(pointId, vector, Map.of(
                    "documentId", documentId,
                    "ownerId", ownerId,
                    "filename", filename != null ? filename : "document.pdf",
                    "chunkIndex", i,
                    "text", chunkText
            )));
        }
        return points;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "Ingestion failed (no message)";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
