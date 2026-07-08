package com.backend.Ai_Gateway.repositories;

import com.backend.Ai_Gateway.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    boolean existsProviderById(Long id);

    Provider findProviderById(Long id);

    List<Provider> findByEnabledTrue();

    boolean existsByName(String name);

    Optional<Provider> findByName(String name);
}
