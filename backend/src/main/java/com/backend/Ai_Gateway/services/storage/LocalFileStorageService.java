package com.backend.Ai_Gateway.services.storage;

import com.backend.Ai_Gateway.config.UploadProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * Local-disk implementation (Phase 1) — files live under {@code app.uploads.storage-path},
 * mounted as a named Docker volume in {@code docker-compose.yml} (survives container
 * rebuilds/restarts, not committed to any image).
 *
 * <p>Storage keys are always server-generated UUIDs ({@code AttachmentService}), never derived
 * from the client-supplied filename, so there is no path-traversal input from the upload path
 * itself — {@link #resolve} still defensively re-validates the key shape and re-checks the
 * resolved path stays under {@link #root} before touching the filesystem, so a future caller
 * passing an untrusted key can't turn into a traversal bug by accident.
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);
    private static final Pattern STORAGE_KEY_PATTERN =
            Pattern.compile("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$");

    private final Path root;

    public LocalFileStorageService(UploadProperties properties) {
        this.root = Path.of(properties.getStoragePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create upload storage directory: " + root, e);
        }
    }

    @Override
    public void store(String storageKey, InputStream data, long sizeBytes) throws IOException {
        Files.copy(data, resolve(storageKey), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream read(String storageKey) throws IOException {
        return Files.newInputStream(resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            log.warn("Failed to delete upload {}: {}", storageKey, e.toString());
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }
}
