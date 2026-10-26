package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditActorTest {

    @Mock UserRepository userRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesActorId_andPrefersPreferredUsernameForLabel() {
        User localUser = user(7L);
        when(userRepository.findAllByKeycloakIdOrderByIdAsc(eq("kc-123"))).thenReturn(List.of(localUser));
        setJwt("kc-123", jwt -> jwt.claim("preferred_username", "alice").claim("email", "alice@x.io"));

        AuditActor actor = AuditActor.fromContext(userRepository);

        assertThat(actor.userId()).isEqualTo(7L);
        assertThat(actor.label()).isEqualTo("alice");
    }

    @Test
    void fallsBackToEmail_whenPreferredUsernameMissing() {
        User localUser = user(7L);
        when(userRepository.findAllByKeycloakIdOrderByIdAsc(eq("kc-123"))).thenReturn(List.of(localUser));
        setJwt("kc-123", jwt -> jwt.claim("email", "alice@x.io"));

        AuditActor actor = AuditActor.fromContext(userRepository);

        assertThat(actor.label()).isEqualTo("alice@x.io");
    }

    @Test
    void fallsBackToSubject_whenNoUsernameOrEmailClaim() {
        User localUser = user(7L);
        when(userRepository.findAllByKeycloakIdOrderByIdAsc(eq("kc-123"))).thenReturn(List.of(localUser));
        setJwt("kc-123", jwt -> jwt);

        AuditActor actor = AuditActor.fromContext(userRepository);

        assertThat(actor.label()).isEqualTo("kc-123");
    }

    @Test
    void noLocalUserMatch_throws() {
        when(userRepository.findAllByKeycloakIdOrderByIdAsc(eq("kc-404"))).thenReturn(List.of());
        setJwt("kc-404", jwt -> jwt);

        assertThatThrownBy(() -> AuditActor.fromContext(userRepository))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kc-404");
    }

    @Test
    void nonJwtAuthentication_throws() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("someone", "n/a"));

        assertThatThrownBy(() -> AuditActor.fromContext(userRepository))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noAuthentication_throws() {
        assertThatThrownBy(() -> AuditActor.fromContext(userRepository))
                .isInstanceOf(IllegalStateException.class);
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("whatever");
        u.setEnabled(true);
        u.setCreatedAt(Instant.now());
        return u;
    }

    private static void setJwt(String subject, java.util.function.Function<Jwt.Builder, Jwt.Builder> customizer) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("scope", "openid");
        Jwt jwt = customizer.apply(builder).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
