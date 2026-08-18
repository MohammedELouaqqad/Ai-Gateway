package com.backend.Ai_Gateway.repositories;

import com.backend.Ai_Gateway.entity.MessageFeedback;
import com.backend.Ai_Gateway.enums.FeedbackRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageFeedbackRepository extends JpaRepository<MessageFeedback, Long> {

    Optional<MessageFeedback> findByMessageIdAndUserId(Long messageId, Long userId);

    List<MessageFeedback> findByUserIdAndMessageIdIn(Long userId, Collection<Long> messageIds);

    long countByRating(FeedbackRating rating);
}
