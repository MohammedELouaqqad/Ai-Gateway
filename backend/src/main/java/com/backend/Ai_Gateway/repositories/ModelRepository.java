package com.backend.Ai_Gateway.repositories;


import com.backend.Ai_Gateway.entity.Model;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModelRepository extends JpaRepository<Model, Long> {
    Model findModelById(Long id);

    Optional<Model> findByIsDefaultTrue();

    Optional<Model> findByName(String name);

    boolean existsByProvider_Id(Long providerId);

    java.util.List<Model> findByProvider_Id(Long providerId);
}
