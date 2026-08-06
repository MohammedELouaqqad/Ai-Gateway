package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAdminControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @Test
    void nonAdmin_isForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canListAndToggleAnotherUser() throws Exception {
        String adminSub = "admin-it-" + UUID.randomUUID();
        User admin = persistUser(adminSub, "admin-user");
        User target = persistUser("target-it-" + UUID.randomUUID(), "target-user");

        mockMvc.perform(get("/api/admin/users")
                        .with(jwt().jwt(j -> j.subject(adminSub))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + target.getId() + ")]").exists());

        mockMvc.perform(patch("/api/admin/users/" + target.getId() + "/status")
                        .with(jwt().jwt(j -> j.subject(adminSub))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(patch("/api/admin/users/" + admin.getId() + "/status")
                        .with(jwt().jwt(j -> j.subject(adminSub))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("propre compte")));
    }

    @Test
    void disabledUser_isRejectedOnNextRequest() throws Exception {
        String sub = "disabled-it-" + UUID.randomUUID();
        User user = persistUser(sub, "soon-disabled");
        user.setEnabled(false);
        userRepository.save(user);

        mockMvc.perform(get("/api/profile").with(jwt().jwt(j -> j.subject(sub))))
                .andExpect(status().isForbidden());
    }

    private User persistUser(String keycloakId, String username) {
        return userRepository.findByKeycloakId(keycloakId).orElseGet(() -> {
            User user = new User();
            user.setKeycloakId(keycloakId);
            user.setUsername(username);
            user.setEmail(username + "@ai-gateway.local");
            user.setEnabled(true);
            user.setCreatedAt(Instant.now());
            return userRepository.save(user);
        });
    }
}
