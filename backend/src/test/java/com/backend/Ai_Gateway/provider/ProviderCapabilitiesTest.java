package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.enums.ProviderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderCapabilitiesTest {

    @Test
    void ollamaOpenAiAzure_supportStreaming() {
        assertTrue(ProviderCapabilities.supportsStreaming(ProviderType.OLLAMA));
        assertTrue(ProviderCapabilities.supportsStreaming(ProviderType.OPENAI));
        assertTrue(ProviderCapabilities.supportsStreaming(ProviderType.AZURE_OPENAI));
    }

    @Test
    void sglangVllm_doNotSupportStreamingYet() {
        assertFalse(ProviderCapabilities.supportsStreaming(ProviderType.SGLANG));
        assertFalse(ProviderCapabilities.supportsStreaming(ProviderType.VLLM));
    }
}
