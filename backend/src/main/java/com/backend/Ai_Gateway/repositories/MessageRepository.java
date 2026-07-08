package com.backend.Ai_Gateway.repositories;

import com.backend.Ai_Gateway.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @Modifying
    @Transactional
    void deleteByConversation_Id(Long conversationId);
    List<Message> findMessagesByConversationId(Long conversationId);
}
