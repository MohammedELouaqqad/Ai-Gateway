package com.backend.Ai_Gateway.services.dto;

import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.enums.FeedbackRating;
import com.backend.Ai_Gateway.enums.MessageRole;

import java.time.Instant;

/** Message list payload including the current user's feedback (if any). */
public record MessageView(
        Long id,
        MessageRole role,
        String content,
        Instant createdAt,
        String modelName,
        String modelDisplayName,
        FeedbackRating myFeedback,
        boolean regenerated,
        Long supersededByMessageId,
        /** Null unless this message had an image attached — fetch bytes via
         *  {@code GET /api/files/{id}}, owner-only (same guarantee as everywhere else). */
        Long attachmentId,
        /** Number of RAG chunks actually retrieved and injected for this reply (assistant
         *  messages only; 0 on user messages and whenever no RAG context was used) — see
         *  {@code Message#ragChunksUsed}. */
        int ragChunksUsed
) {
    public static MessageView from(Message message, FeedbackRating myFeedback) {
        return new MessageView(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt(),
                message.getModelName(),
                message.getModelDisplayName(),
                myFeedback,
                message.isRegenerated(),
                message.getSupersededByMessageId(),
                message.getAttachment() != null ? message.getAttachment().getId() : null,
                message.getRagChunksUsed()
        );
    }
}
