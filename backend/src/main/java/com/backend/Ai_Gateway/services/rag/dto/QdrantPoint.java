package com.backend.Ai_Gateway.services.rag.dto;

import java.util.List;
import java.util.Map;

/** One embedded chunk, ready to upsert into Qdrant. {@code id} is a deterministic UUID derived
 *  from (documentId, chunkIndex) — see {@code RagIngestionJob} — so re-ingesting a document
 *  overwrites its previous points instead of duplicating them. */
public record QdrantPoint(String id, List<Float> vector, Map<String, Object> payload) {}
