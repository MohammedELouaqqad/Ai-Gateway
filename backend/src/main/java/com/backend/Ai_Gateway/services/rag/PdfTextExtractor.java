package com.backend.Ai_Gateway.services.rag;

import jakarta.annotation.PreDestroy;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * PDF → plain text, with two anti-abuse bounds (part of recommendation #5 from the upload
 * security review — deferred in Phase 1 of uploads since it only applies once documents landed):
 *
 * <ul>
 *   <li><b>Time</b>: {@code extractionTimeoutMs} caps how long the caller (the ingestion job
 *       thread) waits. <b>Honest limitation</b>: {@link Future#cancel} on a CPU-bound PDFBox
 *       parse doesn't forcibly kill it (no safe-point checks inside PDFBox's parser to
 *       interrupt) — the runaway parse keeps consuming a background thread even after the
     *   caller gives up. This bounds job-queue starvation (the job moves on to the next
 *       document instead of hanging forever), not the worker thread's own CPU/memory use. Full
 *       protection against a crafted "decompression bomb" PDF needs a separate
 *       process/sandbox — out of scope for v1, called out explicitly rather than silently
 *       assumed solved.</li>
 *   <li><b>Size</b>: output truncated at {@code maxExtractedChars} — never held/chunked/embedded
 *       unbounded regardless of how large the source PDF's actual text turns out to be.</li>
 * </ul>
 */
@Component
public class PdfTextExtractor {

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "pdf-extract");
        t.setDaemon(true);
        return t;
    });

    /** @throws RuntimeException wrapping the real cause on timeout/parse failure — caller
     *  (RagIngestionJob) catches broadly and marks the document FAILED. */
    public String extract(byte[] pdfBytes, long timeoutMs, int maxChars) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> extractBlocking(pdfBytes), executor);
        try {
            String text = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return text.length() > maxChars ? text.substring(0, maxChars) : text;
        } catch (TimeoutException e) {
            future.cancel(true); // best-effort only, see class javadoc
            throw new IllegalStateException("PDF extraction timed out after " + timeoutMs + "ms", e);
        } catch (Exception e) {
            throw new IllegalStateException("PDF extraction failed: " + e.getMessage(), e);
        }
    }

    private String extractBlocking(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
