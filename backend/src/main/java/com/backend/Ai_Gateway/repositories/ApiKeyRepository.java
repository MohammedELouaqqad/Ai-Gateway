package com.backend.Ai_Gateway.repositories;

import com.backend.Ai_Gateway.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findAllByUser_IdOrderByCreatedAtDesc(Long userId);
}
