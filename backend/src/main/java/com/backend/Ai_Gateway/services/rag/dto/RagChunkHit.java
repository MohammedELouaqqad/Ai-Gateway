package com.backend.Ai_Gateway.services.rag.dto;

/** One retrieved chunk — see {@code QdrantClient#search} / {@code RagRetrievalService}. */
public record RagChunkHit(Long documentId, String filename, int chunkIndex, String text, double score) {}
