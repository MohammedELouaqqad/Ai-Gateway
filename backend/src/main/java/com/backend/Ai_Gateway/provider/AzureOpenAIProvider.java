package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Set;

/**
 * Azure OpenAI — different auth scheme ({@code api-key} header, not
 * {@code Authorization: Bearer}), different URL shape
 * ({@code /openai/deployments/{deployment}/chat/completions?api-version=...} instead of
 * plain {@code /chat/completions}), and no shared default endpoint (each customer has their
 * own per-resource base URL, set via {@code Provider.baseUrl}).
 * <p>
 * The deployment name is the {@code Model.name} row as-is (same value already sent in the
 * request body via the default {@link #resolveModelRef}) — Azure requires the deployment
 * name to already be known when creating the {@code Model} row, same as every other family
 * member's model name.
 */
@Component
public class AzureOpenAIProvider extends OpenAiCompatibleProvider {

    /**
     * Azure OpenAI stable GA API version. Azure ships new versions periodically; bump this
     * constant if a newer stable version is needed — there's no per-provider override today,
     * this applies to every Azure OpenAI provider row.
     */
    private static final String API_VERSION = "2024-06-01";

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
                        + "with this resource's endpoint (e.g. https://my-resource.openai.azure.com)."
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

    @Override
    protected String chatPath(Provider provider, ChatRequest request) {
        return "/openai/deployments/" + resolveModelRef(provider, request) + "/chat/completions";
    }

    @Override
    protected Map<String, String> queryParams(Provider provider, ChatRequest request) {
        return Map.of("api-version", API_VERSION);
    }
}
