package com.backend.Ai_Gateway.enums;

/** Lifecycle of a RAG document — see {@code RagIngestionJob} for the PENDING → READY/FAILED
 *  transition. */
public enum RagDocumentStatus {
    /** Uploaded, not yet picked up by the ingestion job. */
    PENDING,
    /** Currently being extracted/chunked/embedded — set at the start of one ingestion pass so a
     *  crash mid-run doesn't look identical to "never started" (still recoverable: the next
     *  job run picks up PROCESSING rows too, see the job's query). */
    PROCESSING,
    /** Chunks embedded and upserted into Qdrant — usable for retrieval. */
    READY,
    /** Extraction/chunking/embedding failed — see {@code errorMessage}. Not retried
     *  automatically (avoids hammering a document that's e.g. genuinely corrupt). */
    FAILED
}
