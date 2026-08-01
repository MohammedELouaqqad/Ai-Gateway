package com.backend.Ai_Gateway.services.dto;

import com.backend.Ai_Gateway.enums.RequestStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UsageLogEntryTest {

    @Test
    void error_zerosTokensAndSetsErrorStatus() {
        UsageLogEntry entry = UsageLogEntry.error(
                1L, 20L, 42L, "explicit", "ACTIVE", "ECONOMY", "gpt-4o");

        assertEquals(1L, entry.userId());
        assertEquals(20L, entry.modelId());
        assertEquals(0, entry.promptTokens());
        assertEquals(0, entry.completionTokens());
        assertEquals(42L, entry.latencyMs());
        assertEquals(RequestStatus.ERROR, entry.status());
        assertEquals("explicit", entry.routingAppliedReason());
        assertEquals("ACTIVE", entry.routingMode());
        assertEquals("ECONOMY", entry.routingProfile());
        assertEquals("gpt-4o", entry.routingSelectedModel());
    }

    @Test
    void success_preservesTokenCountsAndNullRouting() {
        UsageLogEntry entry = UsageLogEntry.success(
                1L, 20L, 3, 2, 15L, "explicit", null, null, null);

        assertEquals(3, entry.promptTokens());
        assertEquals(2, entry.completionTokens());
        assertEquals(RequestStatus.SUCCESS, entry.status());
        assertNull(entry.routingMode());
        assertNull(entry.routingProfile());
        assertNull(entry.routingSelectedModel());
    }
}
