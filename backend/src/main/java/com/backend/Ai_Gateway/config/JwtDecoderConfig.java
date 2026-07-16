package com.backend.Ai_Gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Actif seulement si {@code KEYCLOAK_JWK_SET_URI} est défini (Docker).
 * Sinon Spring utilise {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
 * <p>
 * Les JWT gardent {@code iss=http://localhost:8180/...} ; les clés sont
 * téléchargées via host.docker.internal.
 */
@Configuration
@ConditionalOnProperty(name = "KEYCLOAK_JWK_SET_URI")
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${KEYCLOAK_ISSUER_URI:http://localhost:8180/realms/Ai-Gateway}") String issuerUri,
            @Value("${KEYCLOAK_JWK_SET_URI}") String jwkSetUri
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri.trim()).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri.trim());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer));
        return decoder;
    }
}
