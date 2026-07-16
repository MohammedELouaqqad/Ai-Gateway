package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.enums.ProviderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderCapabilitiesTest {

    @Test
    void ollamaOpenAiAzure_haveAdapters() {
        assertTrue(ProviderCapabilities.hasChatAdapter(ProviderType.OLLAMA));
        assertTrue(ProviderCapabilities.hasStreamAdapter(ProviderType.OLLAMA));
        assertTrue(ProviderCapabilities.hasChatAdapter(ProviderType.OPENAI));
        assertTrue(ProviderCapabilities.hasStreamAdapter(ProviderType.OPENAI));
        assertTrue(ProviderCapabilities.hasChatAdapter(ProviderType.AZURE_OPENAI));
        assertTrue(ProviderCapabilities.hasStreamAdapter(ProviderType.AZURE_OPENAI));
    }

    @Test
    void sglangVllm_haveNoAdaptersInThisProject() {
        assertFalse(ProviderCapabilities.hasChatAdapter(ProviderType.SGLANG));
        assertFalse(ProviderCapabilities.hasStreamAdapter(ProviderType.SGLANG));
        assertFalse(ProviderCapabilities.hasChatAdapter(ProviderType.VLLM));
        assertFalse(ProviderCapabilities.hasStreamAdapter(ProviderType.VLLM));
    }
}
