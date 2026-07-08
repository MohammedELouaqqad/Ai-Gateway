package com.backend.Ai_Gateway.repositories;


import com.backend.Ai_Gateway.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByUser_IdOrderByUpdatedAtDesc(Long userId);

    boolean existsByModel_Id(Long modelId);
}
