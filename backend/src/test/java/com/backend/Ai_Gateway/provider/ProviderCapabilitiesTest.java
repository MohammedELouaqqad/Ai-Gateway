package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.enums.ProviderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderCapabilitiesTest {

    @Test
    void everyProviderType_hasBothAdapters() {
        for (ProviderType type : ProviderType.values()) {
            assertTrue(ProviderCapabilities.hasChatAdapter(type), () -> type + " should have a chat adapter");
            assertTrue(ProviderCapabilities.hasStreamAdapter(type), () -> type + " should have a stream adapter");
        }
    }

    @Test
    void nullType_hasNoAdapter() {
        assertFalse(ProviderCapabilities.hasChatAdapter(null));
        assertFalse(ProviderCapabilities.hasStreamAdapter(null));
    }
}
