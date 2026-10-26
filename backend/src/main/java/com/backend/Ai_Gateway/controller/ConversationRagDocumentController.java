package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.services.RagDocumentService;
import com.backend.Ai_Gateway.services.UserService;
import com.backend.Ai_Gateway.services.dto.RagDocumentView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Attach/detach a RAG document (already uploaded via {@link RagDocumentController}) to a
 * conversation — multiple documents per conversation, a document reusable across conversations
 * (see {@code Conversation.ragDocuments} javadoc). Ownership is checked on BOTH sides
 * ({@code RagDocumentService}): the conversation must belong to the caller, and so must the
 * document — attaching someone else's document is rejected the same 404-not-403 way as every
 * other cross-owner lookup in this codebase.
 */
@RestController
@RequestMapping("/api/conversations/{conversationId}/rag-documents")
public class ConversationRagDocumentController {

    private final RagDocumentService ragDocumentService;
    private final UserService userService;

    public ConversationRagDocumentController(RagDocumentService ragDocumentService, UserService userService) {
        this.ragDocumentService = ragDocumentService;
        this.userService = userService;
    }

    @GetMapping
    public List<RagDocumentView> list(@PathVariable Long conversationId) {
        Long userId = userService.requireCurrentUserId();
        return ragDocumentService.listForConversation(userId, conversationId);
    }

    @PostMapping("/{documentId}")
    public RagDocumentView attach(@PathVariable Long conversationId, @PathVariable Long documentId) {
        Long userId = userService.requireCurrentUserId();
        return ragDocumentService.attachToConversation(userId, conversationId, documentId);
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void detach(@PathVariable Long conversationId, @PathVariable Long documentId) {
        Long userId = userService.requireCurrentUserId();
        ragDocumentService.detachFromConversation(userId, conversationId, documentId);
    }
}
