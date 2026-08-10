package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.enums.ProviderType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderBaseUrlValidatorTest {

    @Test
    void nullOrBlank_alwaysAllowed_forEveryFamily() {
        assertThatCode(() -> ProviderBaseUrlValidator.validate(ProviderType.OPENAI, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProviderBaseUrlValidator.validate(ProviderType.OLLAMA, "  "))
                .doesNotThrowAnyException();
    }

    @Test
    void cloudFamily_requiresHttps() {
        assertThatThrownBy(() -> ProviderBaseUrlValidator.validate(ProviderType.OPENAI, "http://api.openai.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
        assertThatThrownBy(() -> ProviderBaseUrlValidator.validate(ProviderType.AZURE_OPENAI, "http://my-resource.openai.azure.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    @Test
    void cloudFamily_httpsIsAccepted() {
        assertThatCode(() -> ProviderBaseUrlValidator.validate(ProviderType.OPENAI, "https://api.openai.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void selfHostedFamilies_httpAndPrivateHostsAreAllowed() {
        // The whole point of Ollama/vLLM/SGLang is pointing at a LAN/loopback-adjacent host.
        assertThatCode(() -> ProviderBaseUrlValidator.validate(ProviderType.OLLAMA, "http://host.docker.internal:11434"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProviderBaseUrlValidator.validate(ProviderType.VLLM, "http://192.168.1.50:8000"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProviderBaseUrlValidator.validate(ProviderType.SGLANG, "http://10.0.0.5:30000"))
                .doesNotThrowAnyException();
    }

    @Test
    void cloudMetadataEndpoint_blockedForEveryFamily() {
        assertThatThrownBy(() -> ProviderBaseUrlValidator.validate(ProviderType.OPENAI, "https://169.254.169.254/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata");
        assertThatThrownBy(() -> ProviderBaseUrlValidator.validate(ProviderType.OLLAMA, "http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata");
    }

    @Test
    void malformedOrHostless_rejected() {
        assertThatThrownBy(() -> ProviderBaseUrlValidator.validate(ProviderType.OLLAMA, "not a url"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderBaseUrlValidator.validate(ProviderType.OLLAMA, "just-a-hostname-no-scheme"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
    }
}
