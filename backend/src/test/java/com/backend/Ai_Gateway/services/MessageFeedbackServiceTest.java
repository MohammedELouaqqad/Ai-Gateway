package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.entity.MessageFeedback;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.enums.FeedbackRating;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.repositories.MessageFeedbackRepository;
import com.backend.Ai_Gateway.repositories.MessageRepository;
import com.backend.Ai_Gateway.services.dto.ModelQualityStats;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageFeedbackServiceTest {

    @Mock MessageFeedbackRepository feedbackRepository;
    @Mock MessageRepository messageRepository;

    private MessageFeedbackService service;
    private Message assistant;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new MessageFeedbackService(feedbackRepository, messageRepository, new SimpleMeterRegistry());
        owner = new User();
        owner.setId(1L);
        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setUser(owner);
        assistant = new Message();
        assistant.setId(100L);
        assistant.setRole(MessageRole.ASSISTANT);
        assistant.setModelName("llama3.2:1b");
        assistant.setConversation(conversation);
        assistant.setCreatedAt(Instant.now());
    }

    @Test
    void upsert_createsFeedbackForOwner() {
        when(messageRepository.findById(100L)).thenReturn(Optional.of(assistant));
        when(feedbackRepository.findByMessageIdAndUserId(100L, 1L)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any())).thenAnswer(inv -> {
            MessageFeedback f = inv.getArgument(0);
            f.setId(5L);
            return f;
        });

        MessageFeedback saved = service.upsert(1L, 100L, FeedbackRating.UP, "good");

        assertThat(saved.getRating()).isEqualTo(FeedbackRating.UP);
        assertThat(saved.getModelName()).isEqualTo("llama3.2:1b");
        ArgumentCaptor<MessageFeedback> captor = ArgumentCaptor.forClass(MessageFeedback.class);
        verify(feedbackRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    }

    @Test
    void upsert_updatesExisting_idempotent() {
        MessageFeedback existing = new MessageFeedback();
        existing.setId(7L);
        existing.setMessageId(100L);
        existing.setUserId(1L);
        existing.setRating(FeedbackRating.UP);
        existing.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(messageRepository.findById(100L)).thenReturn(Optional.of(assistant));
        when(feedbackRepository.findByMessageIdAndUserId(100L, 1L)).thenReturn(Optional.of(existing));
        when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MessageFeedback saved = service.upsert(1L, 100L, FeedbackRating.DOWN, null);

        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getRating()).isEqualTo(FeedbackRating.DOWN);
    }

    @Test
    void upsert_rejectsNonOwner() {
        when(messageRepository.findById(100L)).thenReturn(Optional.of(assistant));
        assertThrows(UnauthorizedException.class,
                () -> service.upsert(99L, 100L, FeedbackRating.UP, null));
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void upsert_rejectsUserMessages() {
        assistant.setRole(MessageRole.USER);
        when(messageRepository.findById(100L)).thenReturn(Optional.of(assistant));
        assertThrows(IllegalArgumentException.class,
                () -> service.upsert(1L, 100L, FeedbackRating.UP, null));
    }

    @Test
    void aggregateByModel_emptyIsNotMeasuredOverall() {
        when(feedbackRepository.countGroupedByModelAndRating()).thenReturn(List.of());
        assertThat(service.aggregateByModel()).isEmpty();
    }

    @Test
    void aggregateByModel_computesUpRatio_andMeasuredStatus() {
        when(feedbackRepository.countGroupedByModelAndRating()).thenReturn(List.of(
                new Object[]{"llama3.2:1b", FeedbackRating.UP, 3L},
                new Object[]{"llama3.2:1b", FeedbackRating.DOWN, 1L}
        ));
        List<ModelQualityStats> stats = service.aggregateByModel();
        assertThat(stats).hasSize(1);
        ModelQualityStats llama = stats.get(0);
        assertThat(llama.qualityStatus()).isEqualTo(ModelQualityStats.MEASURED);
        assertThat(llama.volume()).isEqualTo(4);
        assertThat(llama.upRatio()).isEqualTo(0.75);
    }

    @Test
    void modelQualityStats_zeroVolume_isNotMeasured() {
        ModelQualityStats empty = ModelQualityStats.of("any", 0, 0);
        assertThat(empty.qualityStatus()).isEqualTo(ModelQualityStats.NOT_MEASURED);
        assertThat(empty.upRatio()).isNull();
    }
}
