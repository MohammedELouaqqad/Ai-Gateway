package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.entity.MessageFeedback;
import com.backend.Ai_Gateway.enums.FeedbackRating;
import com.backend.Ai_Gateway.enums.FeedbackSource;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.repositories.MessageFeedbackRepository;
import com.backend.Ai_Gateway.repositories.MessageRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MessageFeedbackService {

    private final MessageFeedbackRepository feedbackRepository;
    private final MessageRepository messageRepository;
    private final MeterRegistry meterRegistry;

    public MessageFeedbackService(
            MessageFeedbackRepository feedbackRepository,
            MessageRepository messageRepository,
            MeterRegistry meterRegistry
    ) {
        this.feedbackRepository = feedbackRepository;
        this.messageRepository = messageRepository;
        this.meterRegistry = meterRegistry;
    }

    public Map<Long, FeedbackRating> ratingsForUser(Long userId, Collection<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, FeedbackRating> map = new HashMap<>();
        for (MessageFeedback f : feedbackRepository.findByUserIdAndMessageIdIn(userId, messageIds)) {
            map.put(f.getMessageId(), f.getRating());
        }
        return map;
    }

    @Transactional
    public MessageFeedback upsert(
            Long userId,
            Long messageId,
            FeedbackRating rating,
            String reason,
            FeedbackSource source
    ) {
        if (rating == null) {
            throw new IllegalArgumentException("rating: must be UP or DOWN");
        }
        FeedbackSource effectiveSource = source != null ? source : FeedbackSource.EXPLICIT;
        Message message = requireOwnedAssistantMessage(userId, messageId);
        Instant now = Instant.now();
        Optional<MessageFeedback> existing = feedbackRepository.findByMessageIdAndUserId(messageId, userId);
        MessageFeedback feedback = existing.orElseGet(MessageFeedback::new);
        boolean created = feedback.getId() == null;
        if (created) {
            feedback.setMessageId(messageId);
            feedback.setUserId(userId);
            feedback.setCreatedAt(now);
        }
        feedback.setRating(rating);
        feedback.setSource(effectiveSource);
        feedback.setReason(trimReason(reason));
        feedback.setModelName(message.getModelName());
        feedback.setUpdatedAt(now);
        MessageFeedback saved = feedbackRepository.save(feedback);
        meterRegistry.counter(
                "ai_gateway_message_feedback_total",
                "rating", rating.name(),
                "source", effectiveSource.name(),
                "model", sanitize(saved.getModelName()),
                "action", created ? "create" : "update"
        ).increment();
        return saved;
    }

    @Transactional
    public MessageFeedback upsert(Long userId, Long messageId, FeedbackRating rating, String reason) {
        return upsert(userId, messageId, rating, reason, FeedbackSource.EXPLICIT);
    }

    @Transactional
    public void clear(Long userId, Long messageId) {
        requireOwnedAssistantMessage(userId, messageId);
        feedbackRepository.findByMessageIdAndUserId(messageId, userId).ifPresent(feedback -> {
            feedbackRepository.delete(feedback);
            meterRegistry.counter(
                    "ai_gateway_message_feedback_total",
                    "rating", "CLEARED",
                    "model", sanitize(feedback.getModelName()),
                    "action", "clear"
            ).increment();
        });
    }

    public Optional<FeedbackRating> findRating(Long userId, Long messageId) {
        return feedbackRepository.findByMessageIdAndUserId(messageId, userId).map(MessageFeedback::getRating);
    }

    private Message requireOwnedAssistantMessage(Long userId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RessourceNotFoundException("Message not found"));
        if (message.getConversation() == null
                || message.getConversation().getUser() == null
                || !message.getConversation().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Vous n'êtes pas propriétaire de ce message");
        }
        if (message.getRole() != MessageRole.ASSISTANT) {
            throw new IllegalArgumentException("feedback: only assistant messages can be rated");
        }
        return message;
    }

    private static String trimReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private static String sanitize(String model) {
        if (model == null || model.isBlank()) {
            return "unknown";
        }
        return model.length() > 64 ? model.substring(0, 64) : model;
    }
}
