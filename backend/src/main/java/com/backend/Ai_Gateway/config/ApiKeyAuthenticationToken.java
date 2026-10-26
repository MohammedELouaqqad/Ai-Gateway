package com.backend.Ai_Gateway.config;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Authentication for {@code /v1/**} requests authenticated via API key (not Keycloak JWT).
 * Principal is the plain user id, never the {@code User} JPA entity — that entity carries
 * lazy collections (see {@code User.conversation}) that Lombok's {@code @ToString} would
 * try to initialize the moment anything logs this token, outside the request's session.
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final Long userId;

    public ApiKeyAuthenticationToken(Long userId) {
        super(List.of(new SimpleGrantedAuthority("ROLE_API_KEY")));
        this.userId = userId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Long getPrincipal() {
        return userId;
    }
}
