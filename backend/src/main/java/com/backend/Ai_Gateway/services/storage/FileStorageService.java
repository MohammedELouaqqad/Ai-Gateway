package com.backend.Ai_Gateway.services.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where uploaded file bytes physically live. {@link LocalFileStorageService} (disk, behind a
 * Docker volume) is the only implementation for Phase 1 — swapping to an object store
 * (MinIO/S3) later means adding a new implementation behind this interface, not touching any
 * caller ({@code AttachmentService} only ever depends on this interface).
 */
public interface FileStorageService {

    /** Persists the bytes under {@code storageKey} (already server-generated — see caller). */
    void store(String storageKey, InputStream data, long sizeBytes) throws IOException;

    /** @throws IOException if the key doesn't exist. */
    InputStream read(String storageKey) throws IOException;

    /** No-op if the key doesn't exist — deletion must stay idempotent for retention sweeps. */
    void delete(String storageKey);
}
