package com.backend.Ai_Gateway.config;

import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.repositories.UserRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

/**
 * Évite d'appeler Keycloak au démarrage des tests (issuer-uri / JWKS).
 */
@TestConfiguration
public class TestJwtConfig {

    public static final String TEST_SUBJECT = "test-keycloak-sub";

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject(TEST_SUBJECT)
                .claims(claims -> {
                    claims.put("preferred_username", "testuser");
                    claims.put("email", "test@ai-gateway.local");
                })
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    /**
     * New users are created {@code enabled=false} by default (admin approval required — see
     * {@code UserService#createFromJwt}). Tests that authenticate as {@link #TEST_SUBJECT} via
     * {@code SecurityMockMvcRequestPostProcessors.jwt()} bypass the real JWT decoder above, so
     * nothing pre-provisions this user for them — call this in a {@code @BeforeEach} instead.
     */
    public static User ensureEnabledTestUser(UserRepository userRepository) {
        return userRepository.findByKeycloakId(TEST_SUBJECT).orElseGet(() -> {
            User user = new User();
            user.setKeycloakId(TEST_SUBJECT);
            user.setUsername("testuser");
            user.setEmail("test@ai-gateway.local");
            user.setEnabled(true);
            user.setCreatedAt(Instant.now());
            return userRepository.save(user);
        });
    }
}
