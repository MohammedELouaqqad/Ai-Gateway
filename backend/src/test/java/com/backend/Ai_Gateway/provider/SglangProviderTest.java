package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class SglangProviderTest {

    private final SglangProvider provider = new SglangProvider(
            RestClient.builder(), WebClient.builder(), new ObjectMapper());

    @Test
    void types_coversSglangOnly() {
        assertThat(provider.types()).containsExactly(ProviderType.SGLANG);
    }

    @Test
    void defaultBaseUrl_pointsAtLocalSglangDefaultPort() {
        assertThat(provider.defaultBaseUrl()).isEqualTo("http://localhost:30000/v1");
    }

    @Test
    void applyAuth_noKeyConfigured_setsNoAuthorizationHeader() {
        Provider providerRow = new Provider(); // no apiKey — self-hosted, no auth
        HttpHeaders headers = new HttpHeaders();

        provider.applyAuth(providerRow, headers);

        assertThat(headers.get(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void applyAuth_keyConfigured_setsBearerAuthorizationHeader() {
        Provider providerRow = new Provider();
        providerRow.setApiKey("sglang-secret");
        HttpHeaders headers = new HttpHeaders();

        provider.applyAuth(providerRow, headers);

        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sglang-secret");
    }
}
