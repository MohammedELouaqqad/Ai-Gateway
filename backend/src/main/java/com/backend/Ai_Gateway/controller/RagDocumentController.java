package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.services.RagDocumentService;
import com.backend.Ai_Gateway.services.UserService;
import com.backend.Ai_Gateway.services.dto.RagDocumentView;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Self-service RAG document upload — a user manages only their own documents (JWT chain,
 * {@code /api/**}). Phase 1: ingestion only (upload → extract → chunk → embed → Qdrant); NOT
 * yet wired into chat retrieval — see {@code RagIngestionJob}/{@code RagDocumentService}
 * javadoc. Attaching a document to a conversation is a separate endpoint, see
 * {@link ConversationRagDocumentController}.
 */
@RestController
@RequestMapping("/api/rag/documents")
public class RagDocumentController {

    private final RagDocumentService ragDocumentService;
    private final UserService userService;

    public RagDocumentController(RagDocumentService ragDocumentService, UserService userService) {
        this.ragDocumentService = ragDocumentService;
        this.userService = userService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RagDocumentView upload(@RequestParam("file") MultipartFile file) {
        Long userId = userService.requireCurrentUserId();
        return ragDocumentService.upload(userId, file);
    }

    @GetMapping
    public List<RagDocumentView> list() {
        Long userId = userService.requireCurrentUserId();
        return ragDocumentService.list(userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        Long userId = userService.requireCurrentUserId();
        ragDocumentService.delete(userId, id);
    }
}
