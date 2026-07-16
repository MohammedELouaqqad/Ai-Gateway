package com.backend.Ai_Gateway.config;

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
}
