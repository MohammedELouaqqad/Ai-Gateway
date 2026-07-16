package com.backend.Ai_Gateway.repositories;

import com.backend.Ai_Gateway.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByKeycloakId(String keycloakId);

    List<User> findAllByKeycloakIdOrderByIdAsc(String keycloakId);
}
