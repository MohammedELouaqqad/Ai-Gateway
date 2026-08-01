package com.backend.Ai_Gateway.repositories;

import com.backend.Ai_Gateway.entity.MessageFeedback;
import com.backend.Ai_Gateway.enums.FeedbackRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageFeedbackRepository extends JpaRepository<MessageFeedback, Long> {

    Optional<MessageFeedback> findByMessageIdAndUserId(Long messageId, Long userId);

    List<MessageFeedback> findByUserIdAndMessageIdIn(Long userId, Collection<Long> messageIds);

    @Query("""
            select f.modelName, f.rating, count(f)
            from MessageFeedback f
            where f.modelName is not null and f.modelName <> ''
            group by f.modelName, f.rating
            """)
    List<Object[]> countGroupedByModelAndRating();

    long countByRating(FeedbackRating rating);
}
