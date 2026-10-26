package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.repositories.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Resolves who's acting, for {@link AuditService#record}. Reads straight from the
 * {@link SecurityContextHolder} instead of {@link UserService#requireCurrentUser()} on
 * purpose: that method does a find-or-create + profile sync + rejects a locally-disabled
 * account (a real side effect an audit-write should never trigger) — this is a plain,
 * read-only lookup with none of that.
 *
 * <p>JWT-only: every controller this is called from lives under {@code /api/**}, which — per
 * {@code SecurityConfig} — is matched exclusively by the JWT resource-server chain (the API-key
 * chain's {@code securityMatcher} is scoped to {@code /v1/**} and never sees these requests).
 * Branching on {@code ApiKeyAuthenticationToken} here would be dead code for a path that can't
 * occur, not real robustness.
 */
public record AuditActor(Long userId, String label) {

    public static AuditActor fromContext(UserRepository userRepository) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException(
                    "AuditActor.fromContext requires a JWT-authenticated principal, got: "
                            + (auth == null ? "null" : auth.getClass().getSimpleName()));
        }
        Jwt jwt = jwtAuth.getToken();
        String keycloakId = jwt.getSubject();
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new IllegalStateException("JWT subject (sub) missing");
        }

        List<User> matches = userRepository.findAllByKeycloakIdOrderByIdAsc(keycloakId);
        if (matches.isEmpty()) {
            throw new IllegalStateException("No local user found for keycloak id " + keycloakId);
        }

        String label = jwt.getClaimAsString("preferred_username");
        if (label == null || label.isBlank()) {
            label = jwt.getClaimAsString("email");
        }
        if (label == null || label.isBlank()) {
            label = keycloakId;
        }
        return new AuditActor(matches.get(0).getId(), label);
    }
}
