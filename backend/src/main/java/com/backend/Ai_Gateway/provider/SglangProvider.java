package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Set;

/**
 * SGLang's OpenAI-compatible server (`python -m sglang.launch_server ...`, default port
 * 30000) speaks the exact same {@code /chat/completions} wire shape as OpenAI — no
 * path/query overrides needed, just the hooks below. Same optional-auth story as
 * {@link VllmProvider}: self-hosted, commonly no API key at all.
 */
@Component
public class SglangProvider extends OpenAiCompatibleProvider {

    public SglangProvider(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper
    ) {
        super(restClientBuilder, webClientBuilder, objectMapper);
    }

    @Override
    public Set<ProviderType> types() {
        return Set.of(ProviderType.SGLANG);
    }

    @Override
    protected String defaultBaseUrl() {
        return "http://localhost:30000/v1";
    }

    @Override
    protected String resolveApiKey(Provider provider) {
        return provider.getApiKey();
    }

    @Override
    protected void applyAuth(Provider provider, HttpHeaders headers) {
        String key = resolveApiKey(provider);
        if (key != null && !key.isBlank()) {
            headers.setBearerAuth(key.trim());
        }
    }
}
