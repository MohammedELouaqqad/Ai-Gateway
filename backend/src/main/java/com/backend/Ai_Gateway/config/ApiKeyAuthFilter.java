package com.backend.Ai_Gateway.config;

import com.backend.Ai_Gateway.entity.ApiKey;
import com.backend.Ai_Gateway.services.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates {@code /v1/**} requests via {@code Authorization: Bearer <api-key>}.
 * Leaves the request unauthenticated (no exception) on a missing/invalid/revoked key —
 * {@link ApiKeyAuthenticationEntryPoint} turns that into the 401 response.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String rawKey = header.substring(BEARER_PREFIX.length()).trim();
            apiKeyService.authenticate(rawKey).ifPresent(this::authenticate);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * SSE streaming (/v1/chat/completions with stream:true) re-enters the filter chain on
     * Spring's async dispatch to write the body. OncePerRequestFilter's default here is
     * true (skip async re-dispatch) — that left SecurityContext empty on the second pass
     * and 403'd every streaming response after the first bytes were already committed.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private void authenticate(ApiKey apiKey) {
        SecurityContextHolder.getContext()
                .setAuthentication(new ApiKeyAuthenticationToken(apiKey.getUser().getId()));
    }
}
