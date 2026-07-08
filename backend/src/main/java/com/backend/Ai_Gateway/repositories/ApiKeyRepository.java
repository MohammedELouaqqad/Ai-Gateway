package com.backend.Ai_Gateway.repositories;

import com.backend.Ai_Gateway.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
}
