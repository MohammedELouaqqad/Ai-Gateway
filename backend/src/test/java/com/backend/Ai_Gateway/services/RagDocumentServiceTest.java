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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagDocumentServiceTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4\n%fake but valid magic bytes for a test".getBytes();

    @Mock RagDocumentRepository ragDocumentRepository;
    @Mock UserRepository userRepository;
    @Mock ConversationRepository conversationRepository;
    @Mock FileStorageService fileStorageService;
    @Mock QdrantClient qdrantClient;

    private RagDocumentService service;
    private RagProperties properties;
    private User owner;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        properties.setEnabled(true);
        service = new RagDocumentService(
                ragDocumentRepository, userRepository, conversationRepository,
                fileStorageService, qdrantClient, properties);
        owner = new User();
        owner.setId(1L);
    }

    @Test
    void upload_rejectsNonPdfEvenIfNamedDotPdf() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        MockMultipartFile fake = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "not actually a pdf".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upload(1L, fake));
        assertThat(ex.getMessage()).contains("only PDF is accepted");
    }

    @Test
    void upload_rejectsOversizedFile() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        properties.setMaxSizeBytes(4);
        MockMultipartFile tooBig = new MockMultipartFile("file", "a.pdf", "application/pdf", PDF_BYTES);

        assertThrows(IllegalArgumentException.class, () -> service.upload(1L, tooBig));
    }

    @Test
    void upload_disabledRagRejectsUpfront() {
        properties.setEnabled(false);
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", PDF_BYTES);

        assertThrows(IllegalArgumentException.class, () -> service.upload(1L, file));
    }

    @Test
    void upload_acceptsValidPdfAsPending() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(ragDocumentRepository.save(any(RagDocument.class))).thenAnswer(inv -> {
            RagDocument d = inv.getArgument(0);
            d.setId(42L);
            return d;
        });
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", PDF_BYTES);

        RagDocumentView view = service.upload(1L, file);

        assertThat(view.id()).isEqualTo(42L);
        assertThat(view.status()).isEqualTo(RagDocumentStatus.PENDING);
        assertThat(view.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void upload_enforcesPerUserRateLimit() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(ragDocumentRepository.save(any(RagDocument.class))).thenAnswer(inv -> {
            RagDocument d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });
        properties.getRateLimit().setUploadsPerHour(1);
        MockMultipartFile file1 = new MockMultipartFile("file", "a.pdf", "application/pdf", PDF_BYTES);
        MockMultipartFile file2 = new MockMultipartFile("file", "b.pdf", "application/pdf", PDF_BYTES);

        service.upload(1L, file1);
        GuardrailsExceededException ex = assertThrows(GuardrailsExceededException.class,
                () -> service.upload(1L, file2));
        assertThat(ex.getReason()).isEqualTo(GuardrailsExceededException.Reason.UPLOAD_RATE_LIMIT);
    }

    @Test
    void delete_ownerCanDelete_cleansUpQdrantAndDisk() {
        RagDocument doc = documentEntity(owner);
        when(ragDocumentRepository.findById(7L)).thenReturn(Optional.of(doc));

        service.delete(1L, 7L);

        // Must run before the row itself is deleted — conversation_rag_documents has no
        // ON DELETE CASCADE, so skipping this would hit a raw FK violation on a document still
        // attached to a conversation (see RagDocumentService#delete javadoc).
        verify(ragDocumentRepository).detachFromAllConversations(7L);
        verify(qdrantClient).deleteByDocumentId(7L);
        verify(fileStorageService).delete(doc.getStorageKey());
        verify(ragDocumentRepository).delete(doc);
    }

    @Test
    void delete_otherUserGetsNotFound() {
        RagDocument doc = documentEntity(owner);
        when(ragDocumentRepository.findById(7L)).thenReturn(Optional.of(doc));

        assertThrows(RessourceNotFoundException.class, () -> service.delete(2L, 7L));
    }

    @Test
    void attachToConversation_rejectsWhenConversationNotOwned() {
        Conversation conversation = new Conversation();
        conversation.setId(5L);
        User otherOwner = new User();
        otherOwner.setId(99L);
        conversation.setUser(otherOwner);
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        assertThrows(UnauthorizedException.class, () -> service.attachToConversation(1L, 5L, 7L));
    }

    @Test
    void attachToConversation_rejectsWhenDocumentNotOwned() {
        Conversation conversation = new Conversation();
        conversation.setId(5L);
        conversation.setUser(owner);
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        RagDocument othersDoc = documentEntity(new User());
        othersDoc.getOwner().setId(99L);
        when(ragDocumentRepository.findById(7L)).thenReturn(Optional.of(othersDoc));

        assertThrows(RessourceNotFoundException.class, () -> service.attachToConversation(1L, 5L, 7L));
    }

    @Test
    void attachToConversation_success_addsDocumentOnceEvenIfCalledTwice() {
        Conversation conversation = new Conversation();
        conversation.setId(5L);
        conversation.setUser(owner);
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        RagDocument doc = documentEntity(owner);
        when(ragDocumentRepository.findById(7L)).thenReturn(Optional.of(doc));

        service.attachToConversation(1L, 5L, 7L);
        service.attachToConversation(1L, 5L, 7L);

        assertThat(conversation.getRagDocuments()).hasSize(1);
    }

    private RagDocument documentEntity(User owner) {
        RagDocument doc = new RagDocument();
        doc.setId(7L);
        doc.setOwner(owner);
        doc.setStorageKey("11111111-1111-1111-1111-111111111111");
        doc.setOriginalFilename("report.pdf");
        doc.setContentType("application/pdf");
        doc.setSizeBytes(PDF_BYTES.length);
        doc.setStatus(RagDocumentStatus.READY);
        return doc;
    }
}
