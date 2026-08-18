package com.backend.Ai_Gateway.repositories;

import com.backend.Ai_Gateway.entity.RagDocument;
import com.backend.Ai_Gateway.enums.RagDocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RagDocumentRepository extends JpaRepository<RagDocument, Long> {

    List<RagDocument> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);

    /** Used by {@code RagIngestionJob} — includes PROCESSING so a row orphaned by a crash
     *  mid-run (see {@link RagDocumentStatus#PROCESSING} javadoc) gets picked up again. */
    List<RagDocument> findByStatusIn(List<RagDocumentStatus> statuses);

    /**
     * Direct join query — deliberately NOT {@code conversation.getRagDocuments()} navigation.
     * {@code Conversation.ragDocuments} is a lazy {@code @ManyToMany} (JPA default for
     * many-to-many, unlike the eager {@code @ManyToOne}s elsewhere on that entity); by the time
     * {@code RagRetrievalService} runs, the session that loaded the conversation is already
     * closed, so touching that collection there throws {@code LazyInitializationException}.
     * A fresh, self-contained query sidesteps the problem instead of forcing the whole
     * collection eager (which would tax every conversation load, not just RAG-enabled ones).
     */
    @Query("SELECT d.id FROM Conversation c JOIN c.ragDocuments d "
            + "WHERE c.id = :conversationId AND d.status = :status")
    List<Long> findDocumentIdsByConversationIdAndStatus(
            @Param("conversationId") Long conversationId, @Param("status") RagDocumentStatus status);
}
