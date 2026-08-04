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
 * Azure OpenAI — same {@code /chat/completions} wire shape as OpenAI, but a different
 * auth scheme ({@code api-key} header, not {@code Authorization: Bearer}) and no shared
 * default endpoint (each customer has their own per-resource base URL).
 * <p>
 * Note: Azure's real endpoint shape also differs by URL path
 * ({@code /openai/deployments/{name}/chat/completions?api-version=...}), which this class
 * does not yet address — {@link #defaultBaseUrl()} requires the full path to be configured
 * on the {@code Provider} row until that's built.
 */
@Component
public class AzureOpenAIProvider extends OpenAiCompatibleProvider {

    public AzureOpenAIProvider(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper
    ) {
        super(restClientBuilder, webClientBuilder, objectMapper);
    }

    @Override
    public Set<ProviderType> types() {
        return Set.of(ProviderType.AZURE_OPENAI);
    }

    @Override
    protected String defaultBaseUrl() {
        throw new IllegalArgumentException(
                "Azure OpenAI has no shared default endpoint. Configure Provider.baseUrl "
                        + "with this resource's full endpoint."
        );
    }

    @Override
    protected String resolveApiKey(Provider provider) {
        if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
            return provider.getApiKey().trim();
        }
        throw new IllegalArgumentException(
                "Azure OpenAI API key not configured on this Provider row."
        );
    }

    @Override
    protected void applyAuth(Provider provider, HttpHeaders headers) {
        headers.set("api-key", resolveApiKey(provider));
    }
}
