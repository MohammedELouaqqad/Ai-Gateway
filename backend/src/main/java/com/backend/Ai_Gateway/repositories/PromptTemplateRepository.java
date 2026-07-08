package com.backend.Ai_Gateway.repositories;

import com.backend.Ai_Gateway.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {
}
