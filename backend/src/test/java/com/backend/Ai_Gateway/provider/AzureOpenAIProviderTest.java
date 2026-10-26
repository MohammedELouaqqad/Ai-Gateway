package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureOpenAIProviderTest {

    private final AzureOpenAIProvider provider = new AzureOpenAIProvider(
            RestClient.builder(), WebClient.builder(), new ObjectMapper());

    @Test
    void types_coversAzureOpenAiOnly() {
        assertThat(provider.types()).containsExactly(ProviderType.AZURE_OPENAI);
    }

    @Test
    void defaultBaseUrl_hasNoSharedDefault() {
        // protected, but this test is in the same package — same access rule as a subclass.
        assertThatThrownBy(provider::defaultBaseUrl)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no shared default endpoint");
    }

    @Test
    void resolveApiKey_requiresProviderRowKey() {
        Provider withoutKey = new Provider();
        assertThatThrownBy(() -> provider.resolveApiKey(withoutKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Azure OpenAI API key not configured");
    }
}
