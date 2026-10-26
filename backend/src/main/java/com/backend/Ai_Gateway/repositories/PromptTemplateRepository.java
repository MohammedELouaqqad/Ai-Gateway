package com.backend.Ai_Gateway.repositories;

import com.backend.Ai_Gateway.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    List<PromptTemplate> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<PromptTemplate> findByUser_IdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUser_IdAndNameIgnoreCase(Long userId, String name);
}
