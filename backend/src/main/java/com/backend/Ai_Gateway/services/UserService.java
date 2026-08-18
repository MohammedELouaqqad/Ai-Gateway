package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    /**
     * Confort dev uniquement — {@code true} sur le profil {@code dev} (application-dev.properties),
     * {@code false} partout ailleurs (défaut de base + explicite en prod). Évite de recliquer
     * "Activer" en admin à chaque inscription de test ; en prod le garde-fou budget reste intact
     * par défaut (voir {@link #createFromJwt}).
     */
    @Value("${app.users.auto-enable-new:false}")
    private boolean autoEnableNewUsers;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Résout l'utilisateur authentifié depuis le JWT Keycloak (find-or-create).
     * Tolère les doublons historiques sur {@code keycloak_id} (renvoie le plus ancien id).
     * Rejette un compte désactivé par un admin (voir {@link #toggleStatus}) — ne le
     * réactive jamais tout seul, contrairement à l'ancien comportement.
     * <p>
     * Rejette aussi un email explicitement marqué non vérifié ({@code email_verified: false}).
     * Défense en profondeur : si le realm Keycloak a "Verify email" activé (recommandé dès que
     * l'inscription publique l'est aussi), un compte non vérifié ne devrait déjà jamais obtenir
     * de JWT valide — ce contrôle couvre le cas d'un realm mal configuré.
     * <p>
     * Délibérément <b>pas</b> {@code @Transactional} : si cette méthode l'était, le
     * {@code save()} fait par {@link #createFromJwt} pour un nouveau compte serait annulé par
     * le rollback automatique déclenché par le {@code throw} juste après ("Compte désactivé")
     * — le nouvel utilisateur ne serait alors jamais persisté, et un ADMIN ne pourrait jamais
     * le voir dans Users pour l'activer (bug réel, trouvé en test manuel). Chaque
     * {@code save()}/requête de repository ci-dessous a déjà sa propre transaction courte via
     * Spring Data JPA — pas besoin d'une transaction englobante pour ce flux find-or-create.
     */
    public User requireCurrentUser() {
        Jwt jwt = currentJwt();
        String keycloakId = jwt.getSubject();
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new UnauthorizedException("JWT subject (sub) manquant");
        }
        if (Boolean.FALSE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new UnauthorizedException("Email non vérifié");
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

    /** Same reasoning as {@link #requireCurrentUser()} — deliberately not {@code @Transactional}. */
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

    /**
     * Un compte tout juste vu pour la première fois démarre {@code enabled=false} — qu'il
     * vienne d'une auto-inscription publique Keycloak ou d'un compte créé à la main par un
     * admin dans Keycloak, l'app ne peut pas distinguer les deux à ce stade (aucun claim JWT
     * standard ne le dit). Un ADMIN doit l'activer explicitement dans Users avant tout accès —
     * seule façon d'éviter qu'une inscription publique ouverte consomme du budget LLM sans
     * contrôle. Voir {@link #toggleStatus}.
     * <p>
     * Deux exceptions, toutes deux sûres car elles ne concernent jamais l'auto-inscription
     * publique (qui ne donne que le rôle {@code USER} par défaut — voir realm import,
     * {@code default-roles-ai-gateway}) :
     * <ul>
     *   <li>{@code autoEnableNewUsers} (profil {@code dev} uniquement) — confort de test, jamais
     *       actif par défaut ni en prod.</li>
     *   <li>Rôle {@code ADMIN} présent dans le JWT — assigner ce rôle dans Keycloak est déjà un
     *       geste délibéré d'un opérateur Keycloak, pas quelque chose qu'une inscription
     *       publique peut obtenir. Sans ça, le tout premier admin d'un clone frais (aucun autre
     *       admin encore activé pour l'activer) reste bloqué en boucle sans issue — un vrai
     *       deadlock de bootstrap, corrigé ici.</li>
     * </ul>
     */
    private User createFromJwt(Jwt jwt, String keycloakId) {
        User user = new User();
        user.setKeycloakId(keycloakId);
        user.setUsername(claim(jwt, "preferred_username") != null
                ? claim(jwt, "preferred_username")
                : keycloakId);
        user.setEmail(claim(jwt, "email"));
        user.setEnabled(autoEnableNewUsers || hasAdminRole(jwt));
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

    /** Même source que {@code SecurityConfig.extractAuthorities} (realm_access.roles), lu ici en brut. */
    @SuppressWarnings("unchecked")
    private static boolean hasAdminRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return false;
        }
        return roles.stream().map(Object::toString).anyMatch("ADMIN"::equals);
    }
}
