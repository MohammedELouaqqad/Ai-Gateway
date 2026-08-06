package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;

    @InjectMocks UserService userService;

    @BeforeEach
    void bindJwt() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("some-sub")
                .claim("preferred_username", "someuser")
                .claim("email", "some@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.setContext(new SecurityContextImpl(new JwtAuthenticationToken(jwt)));
    }

    @Test
    void requireCurrentUser_disabledAccount_isRejected_neverAutoReenabled() {
        User disabled = new User();
        disabled.setId(5L);
        disabled.setKeycloakId("some-sub");
        disabled.setUsername("someuser");
        disabled.setEnabled(false);
        when(userRepository.findAllByKeycloakIdOrderByIdAsc("some-sub")).thenReturn(List.of(disabled));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> userService.requireCurrentUser())
                .isInstanceOf(UnauthorizedException.class);

        // syncProfile may save for username/email drift, but must never flip enabled back to true.
        assertThat(disabled.isEnabled()).isFalse();
    }

    @Test
    void requireCurrentUser_enabledAccount_passesThrough() {
        User enabled = new User();
        enabled.setId(5L);
        enabled.setKeycloakId("some-sub");
        enabled.setUsername("someuser");
        enabled.setEmail("some@example.com"); // matches the JWT claim — keeps syncProfile a no-op, no save() stub needed
        enabled.setEnabled(true);
        when(userRepository.findAllByKeycloakIdOrderByIdAsc("some-sub")).thenReturn(List.of(enabled));

        User resolved = userService.requireCurrentUser();

        assertThat(resolved.getId()).isEqualTo(5L);
        assertThat(resolved.isEnabled()).isTrue();
    }

    @Test
    void toggleStatus_flipsEnabledFlag() {
        User target = new User();
        target.setId(10L);
        target.setEnabled(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.toggleStatus(10L, 1L);

        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    void toggleStatus_ownAccount_isRejected() {
        assertThatThrownBy(() -> userService.toggleStatus(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void toggleStatus_unknownUser_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.toggleStatus(99L, 1L))
                .isInstanceOf(RessourceNotFoundException.class);
    }
}
