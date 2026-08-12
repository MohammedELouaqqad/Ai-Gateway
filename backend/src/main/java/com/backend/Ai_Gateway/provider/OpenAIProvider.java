package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.provider.dto.ProviderModelInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Set;

/** Public OpenAI only — Azure OpenAI is {@link AzureOpenAIProvider} (different auth scheme). */
@Component
public class OpenAIProvider extends OpenAiCompatibleProvider implements ModelListingProvider {

    private final String defaultOpenAiApiKey;

    public OpenAIProvider(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${OPENAI_API_KEY:${app.openai.api-key:}}") String defaultOpenAiApiKey
    ) {
        super(restClientBuilder, webClientBuilder, objectMapper);
        this.defaultOpenAiApiKey = defaultOpenAiApiKey;
    }

    @Override
    public Set<ProviderType> types() {
        return Set.of(ProviderType.OPENAI);
    }

    @Override
    protected String defaultBaseUrl() {
        return "https://api.openai.com/v1";
    }

    @Override
    protected String resolveApiKey(Provider provider) {
        if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
            return provider.getApiKey().trim();
        }
        if (defaultOpenAiApiKey != null && !defaultOpenAiApiKey.isBlank()) {
            return defaultOpenAiApiKey.trim();
        }
        throw new IllegalArgumentException(
                "OpenAI API key not configured. Set OPENAI_API_KEY or configure the provider key."
        );
    }

    @Override
    protected void applyAuth(Provider provider, HttpHeaders headers) {
        headers.setBearerAuth(resolveApiKey(provider));
    }

    @Override
    public List<ProviderModelInfo> models(Provider provider) {
        return listModelsOpenAiStyle(provider);
    }
}
