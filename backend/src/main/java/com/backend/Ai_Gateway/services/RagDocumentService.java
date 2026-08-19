package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.RagProperties;
import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.RagDocument;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.enums.RagDocumentStatus;
import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.RagDocumentRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.RagDocumentView;
import com.backend.Ai_Gateway.services.rag.QdrantClient;
import com.backend.Ai_Gateway.services.storage.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Self-service RAG document lifecycle — upload, list, delete, and attach/detach to
 * conversations. Ingestion itself (extract/chunk/embed/upsert) is {@code RagIngestionJob}, not
 * here — this service only ever leaves a document in {@code PENDING}; the job does the heavy
 * lifting off the request thread.
 */
@Service
public class RagDocumentService {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    private final RagDocumentRepository ragDocumentRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final FileStorageService fileStorageService;
    private final QdrantClient qdrantClient;
    private final RagProperties properties;

    /** Same independent-from-LLM-guardrails rationale as {@code AttachmentService}'s upload
     *  limiter — bounds ingestion volume, not LLM spend. */
    private final Map<Long, Deque<Instant>> uploadTimestampsByUser = new ConcurrentHashMap<>();

    public RagDocumentService(
            RagDocumentRepository ragDocumentRepository,
            UserRepository userRepository,
            ConversationRepository conversationRepository,
            FileStorageService fileStorageService,
            QdrantClient qdrantClient,
            RagProperties properties
    ) {
        this.ragDocumentRepository = ragDocumentRepository;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.fileStorageService = fileStorageService;
        this.qdrantClient = qdrantClient;
        this.properties = properties;
    }

    @Transactional
    public RagDocumentView upload(Long userId, MultipartFile file) {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("file: RAG is disabled");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RessourceNotFoundException("User not found"));

        enforceUploadRateLimit(userId);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file: must not be empty");
        }
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new IllegalArgumentException(
                    "file: exceeds max size of " + properties.getMaxSizeBytes() + " bytes");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("file: could not be read");
        }
        if (bytes.length > properties.getMaxSizeBytes()) {
            throw new IllegalArgumentException(
                    "file: exceeds max size of " + properties.getMaxSizeBytes() + " bytes");
        }
        if (!isPdf(bytes)) {
            throw new IllegalArgumentException("file: unsupported or unrecognized type — only PDF is accepted");
        }

        RagDocument document = new RagDocument();
        document.setOwner(user);
        document.setStorageKey(UUID.randomUUID().toString());
        document.setOriginalFilename(sanitizeFilename(file.getOriginalFilename()));
        document.setContentType("application/pdf");
        document.setSizeBytes(bytes.length);
        document.setSha256(sha256Hex(bytes));
        document.setStatus(RagDocumentStatus.PENDING);
        document.setChunkCount(0);
        document.setCreatedAt(Instant.now());

        // DB row first, then bytes — same crash-safety reasoning as AttachmentService#upload.
        RagDocument saved = ragDocumentRepository.save(document);
        try {
            fileStorageService.store(saved.getStorageKey(), new ByteArrayInputStream(bytes), bytes.length);
        } catch (IOException e) {
            ragDocumentRepository.delete(saved);
            throw new IllegalStateException("Failed to persist upload", e);
        }

        return RagDocumentView.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RagDocumentView> list(Long userId) {
        return ragDocumentRepository.findByOwner_IdOrderByCreatedAtDesc(userId).stream()
                .map(RagDocumentView::from)
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long documentId) {
        RagDocument document = requireOwned(userId, documentId);
        // Must run before ragDocumentRepository.delete() below — conversation_rag_documents has
        // no ON DELETE CASCADE (verified against the schema), so deleting a document still
        // attached to a conversation would otherwise hit a raw Postgres FK violation.
        ragDocumentRepository.detachFromAllConversations(document.getId());
        qdrantClient.deleteByDocumentId(document.getId());
        fileStorageService.delete(document.getStorageKey());
        ragDocumentRepository.delete(document);
    }

    @Transactional
    public RagDocumentView attachToConversation(Long userId, Long conversationId, Long documentId) {
        Conversation conversation = requireOwnedConversation(userId, conversationId);
        RagDocument document = requireOwned(userId, documentId);
        if (conversation.getRagDocuments().stream().noneMatch(d -> d.getId().equals(documentId))) {
            conversation.getRagDocuments().add(document);
            conversationRepository.save(conversation);
        }
        return RagDocumentView.from(document);
    }

    @Transactional
    public void detachFromConversation(Long userId, Long conversationId, Long documentId) {
        Conversation conversation = requireOwnedConversation(userId, conversationId);
        conversation.getRagDocuments().removeIf(d -> d.getId().equals(documentId));
        conversationRepository.save(conversation);
    }

    @Transactional(readOnly = true)
    public List<RagDocumentView> listForConversation(Long userId, Long conversationId) {
        Conversation conversation = requireOwnedConversation(userId, conversationId);
        return conversation.getRagDocuments().stream().map(RagDocumentView::from).toList();
    }

    /** 404, not 403, on ownership mismatch — same IDOR-avoidance pattern as everywhere else
     *  in this codebase (see AttachmentService/ConversationService). */
    private RagDocument requireOwned(Long userId, Long documentId) {
        RagDocument document = ragDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RessourceNotFoundException("Document not found"));
        if (document.getOwner() == null || !document.getOwner().getId().equals(userId)) {
            throw new RessourceNotFoundException("Document not found");
        }
        return document;
    }

    private Conversation requireOwnedConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RessourceNotFoundException("Conversation not found"));
        if (conversation.getUser() == null || !conversation.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Vous n'êtes pas propriétaire de cette conversation");
        }
        return conversation;
    }

    private void enforceUploadRateLimit(Long userId) {
        int limit = properties.getRateLimit().getUploadsPerHour();
        if (limit <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        Deque<Instant> timestamps = uploadTimestampsByUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                throw new GuardrailsExceededException(
                        GuardrailsExceededException.Reason.UPLOAD_RATE_LIMIT,
                        "Limite d'upload de documents : max " + limit + " / heure.",
                        Instant.now().plusSeconds(3600)
                );
            }
            timestamps.addLast(Instant.now());
        }
    }

    private static boolean isPdf(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "document.pdf";
        }
        String base = name.replaceAll("[\\\\/]", "_").trim();
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
