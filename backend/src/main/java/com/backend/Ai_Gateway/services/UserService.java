package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Résout l'utilisateur authentifié depuis le JWT Keycloak (find-or-create).
     * Tolère les doublons historiques sur {@code keycloak_id} (renvoie le plus ancien id).
     * Rejette un compte désactivé par un admin (voir {@link #toggleStatus}) — ne le
     * réactive jamais tout seul, contrairement à l'ancien comportement.
     */
    @Transactional
    public User requireCurrentUser() {
        Jwt jwt = currentJwt();
        String keycloakId = jwt.getSubject();
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new UnauthorizedException("JWT subject (sub) manquant");
        }

        List<User> matches = userRepository.findAllByKeycloakIdOrderByIdAsc(keycloakId);
        User user;
        if (!matches.isEmpty()) {
            reconcileDuplicateKeycloakIds(matches, keycloakId);
            user = syncProfile(matches.get(0), jwt);
        } else {
            try {
                user = createFromJwt(jwt, keycloakId);
            } catch (DataIntegrityViolationException ex) {
                // Course parallèle create — recharger
                user = userRepository.findAllByKeycloakIdOrderByIdAsc(keycloakId).stream()
                        .findFirst()
                        .map(existing -> syncProfile(existing, jwt))
                        .orElseThrow(() -> new UnauthorizedException("Impossible de résoudre l'utilisateur JWT"));
            }
        }

        if (!user.isEnabled()) {
            throw new UnauthorizedException("Compte désactivé");
        }
        return user;
    }

    @Transactional
    public Long requireCurrentUserId() {
        return requireCurrentUser().getId();
    }

    public List<User> listAll() {
        return userRepository.findAll();
    }

    /**
     * Admin-only (enforced by the controller). An admin can never disable their own
     * account this way — a self-lockout would need a direct DB fix to undo.
     */
    @Transactional
    public User toggleStatus(Long targetUserId, Long actingUserId) {
        if (targetUserId.equals(actingUserId)) {
            throw new IllegalArgumentException("Vous ne pouvez pas désactiver votre propre compte");
        }
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RessourceNotFoundException("User not found"));
        user.setEnabled(!user.isEnabled());
        return userRepository.save(user);
    }

    /**
     * Conserve le premier user (id min) ; détache les doublons pour rétablir l'unicité.
     */
    private void reconcileDuplicateKeycloakIds(List<User> matches, String keycloakId) {
        if (matches.size() <= 1) {
            return;
        }
        log.warn(
                "Doublons users.keycloak_id={} (count={}) — conservation id={}, détachement des autres",
                keycloakId,
                matches.size(),
                matches.get(0).getId()
        );
        for (int i = 1; i < matches.size(); i++) {
            User dup = matches.get(i);
            dup.setKeycloakId(keycloakId + "-orphaned-" + dup.getId());
            dup.setEnabled(false);
            userRepository.save(dup);
        }
    }

    private User syncProfile(User user, Jwt jwt) {
        String username = claim(jwt, "preferred_username");
        String email = claim(jwt, "email");
        boolean dirty = false;
        if (username != null && !username.equals(user.getUsername())) {
            user.setUsername(username);
            dirty = true;
        }
        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            dirty = true;
        }
        return dirty ? userRepository.save(user) : user;
    }

    private User createFromJwt(Jwt jwt, String keycloakId) {
        User user = new User();
        user.setKeycloakId(keycloakId);
        user.setUsername(claim(jwt, "preferred_username") != null
                ? claim(jwt, "preferred_username")
                : keycloakId);
        user.setEmail(claim(jwt, "email"));
        user.setEnabled(true);
        user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }

    private Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        throw new UnauthorizedException("Authentification JWT Keycloak requise");
    }

    private static String claim(Jwt jwt, String name) {
        String value = jwt.getClaimAsString(name);
        return value != null && !value.isBlank() ? value : null;
    }
}
