package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceDuplicateKeycloakIdTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void bindJwt() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("dup-sub")
                .claim("preferred_username", "devuser")
                .claim("email", "dev@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.setContext(new SecurityContextImpl(new JwtAuthenticationToken(jwt)));
    }

    @Test
    void requireCurrentUser_reconcilesDuplicateKeycloakIds() {
        User first = new User();
        first.setId(1L);
        first.setKeycloakId("dup-sub");
        first.setUsername("old");
        first.setEnabled(true);

        User second = new User();
        second.setId(2L);
        second.setKeycloakId("dup-sub");
        second.setUsername("dup");
        second.setEnabled(true);

        when(userRepository.findAllByKeycloakIdOrderByIdAsc("dup-sub")).thenReturn(List.of(first, second));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User resolved = userService.requireCurrentUser();

        assertEquals(1L, resolved.getId());
        assertEquals("devuser", resolved.getUsername());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(saved.capture());
        User orphan = saved.getAllValues().stream()
                .filter(u -> u.getId() != null && u.getId() == 2L)
                .findFirst()
                .orElseThrow();
        assertEquals("dup-sub-orphaned-2", orphan.getKeycloakId());

        verify(userRepository, never()).findByKeycloakId(any());
    }
}
