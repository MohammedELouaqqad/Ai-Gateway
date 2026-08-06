package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.services.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Local user management (list/enable/disable) — roles themselves still come exclusively
 * from Keycloak (see {@code SecurityConfig}), not from this table. No role editing here.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<User> list() {
        return userService.listAll();
    }

    @PatchMapping("/{id}/status")
    public User toggleStatus(@PathVariable Long id) {
        Long actingUserId = userService.requireCurrentUserId();
        return userService.toggleStatus(id, actingUserId);
    }
}
