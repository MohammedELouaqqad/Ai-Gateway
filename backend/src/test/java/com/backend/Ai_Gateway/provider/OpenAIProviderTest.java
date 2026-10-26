package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.enums.ProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIProviderTest {

    @Test
    void types_coversOpenAiOnly() {
        OpenAIProvider provider = new OpenAIProvider(
                RestClient.builder(),
                WebClient.builder(),
                new ObjectMapper(),
                "test-key"
        );

        assertThat(provider.types()).containsExactly(ProviderType.OPENAI);
    }
}
