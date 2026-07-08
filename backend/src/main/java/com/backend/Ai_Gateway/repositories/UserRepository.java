package com.backend.Ai_Gateway.repositories;

import com.backend.Ai_Gateway.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
