package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.services.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Map<String, Object> me() {
        User user = userService.requireCurrentUser();
        return Map.of(
                "id", user.getId(),
                "keycloakId", user.getKeycloakId() != null ? user.getKeycloakId() : "",
                "username", user.getUsername() != null ? user.getUsername() : "",
                "email", user.getEmail() != null ? user.getEmail() : "",
                "enabled", user.isEnabled()
        );
    }
}
