package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.AuditLog;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.enums.AuditAction;
import com.backend.Ai_Gateway.repositories.AuditLogRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;

    private SimpleMeterRegistry meterRegistry;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        auditService = new AuditService(auditLogRepository, userRepository, meterRegistry);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void record_success_savesEntryWithResolvedActor() {
        authenticateAs("kc-42", 5L, "alice");

        auditService.record(AuditAction.PROVIDER_DELETE, "PROVIDER", 9L, "OpenAI Prod", Map.of("k", "v"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActorUserId()).isEqualTo(5L);
        assertThat(saved.getActorLabel()).isEqualTo("alice");
        assertThat(saved.getAction()).isEqualTo(AuditAction.PROVIDER_DELETE);
        assertThat(saved.getTargetType()).isEqualTo("PROVIDER");
        assertThat(saved.getTargetId()).isEqualTo(9L);
        assertThat(saved.getTargetLabel()).isEqualTo("OpenAI Prod");
        assertThat(saved.getDetails()).isEqualTo(Map.of("k", "v"));
        assertThat(saved.getCreatedAt()).isNotNull();

        assertThat(meterRegistry.find("ai_gateway_audit_write_failures_total").counter()).isNull();
    }

    @Test
    void record_repositorySaveThrows_doesNotPropagate_andIncrementsFailureCounter() {
        authenticateAs("kc-42", 5L, "alice");
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> auditService.record(AuditAction.MODEL_DELETE, "MODEL", 1L, "m", null))
                .doesNotThrowAnyException();

        assertThat(meterRegistry.counter("ai_gateway_audit_write_failures_total", "action", "MODEL_DELETE").count())
                .isEqualTo(1.0);
    }

    @Test
    void record_actorResolutionFails_doesNotPropagate_andIncrementsFailureCounter() {
        // No authentication set in SecurityContext at all — AuditActor.fromContext throws.
        assertThatCode(() -> auditService.record(AuditAction.USER_ENABLE, "USER", 1L, "bob", null))
                .doesNotThrowAnyException();

        verify(auditLogRepository, never()).save(any());
        assertThat(meterRegistry.counter("ai_gateway_audit_write_failures_total", "action", "USER_ENABLE").count())
                .isEqualTo(1.0);
    }

    @Test
    void search_delegatesToRepositoryWithSpec() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        Page<AuditLog> result = auditService.search(AuditAction.GUARDRAILS_UPDATE, null, null, pageable);

        assertThat(result).isEmpty();
    }

    private void authenticateAs(String keycloakId, Long localUserId, String username) {
        User user = new User();
        user.setId(localUserId);
        user.setUsername(username);
        user.setEnabled(true);
        user.setCreatedAt(Instant.now());
        lenient().when(userRepository.findAllByKeycloakIdOrderByIdAsc(eq(keycloakId))).thenReturn(List.of(user));

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(keycloakId)
                .claim("preferred_username", username)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
