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
 * vLLM's OpenAI-compatible server (`vllm serve ...`, default port 8000) speaks the exact same
 * {@code /chat/completions} wire shape as OpenAI — no path/query overrides needed, just the
 * hooks below.
 * <p>
 * Auth is optional, unlike OpenAI/Azure: a self-hosted vLLM instance commonly runs with no
 * API key at all (private network / local machine). If the operator did start vLLM with
 * {@code --api-key ...}, configuring {@code Provider.apiKey} sends it as a Bearer token;
 * otherwise no {@code Authorization} header is sent at all — never throws for a missing key.
 */
@Component
public class VllmProvider extends OpenAiCompatibleProvider {

    public VllmProvider(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper
    ) {
        super(restClientBuilder, webClientBuilder, objectMapper);
    }

    @Override
    public Set<ProviderType> types() {
        return Set.of(ProviderType.VLLM);
    }

    @Override
    protected String defaultBaseUrl() {
        return "http://localhost:8000/v1";
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
