package com.backend.Ai_Gateway.repositories;


import com.backend.Ai_Gateway.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByUser_IdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long userId);

    List<Conversation> findByDeletedAtIsNotNullAndDeletedAtBefore(Instant cutoff);

    boolean existsByModel_Id(Long modelId);
}
