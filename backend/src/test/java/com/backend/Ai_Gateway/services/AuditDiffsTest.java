package com.backend.Ai_Gateway.services;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditDiffsTest {

    @Test
    void onlyChangedFieldsAppear() {
        Map<String, Object> before = Map.of("name", "old-name", "priority", 1);
        Map<String, Object> after = Map.of("name", "new-name", "priority", 1);

        Map<String, Object> diff = AuditDiffs.diff(before, after);

        assertThat(diff).containsOnlyKeys("name");
        assertThat(diff.get("name")).isEqualTo(Map.of("old", "old-name", "new", "new-name"));
    }

    @Test
    void noChanges_emptyDiff() {
        Map<String, Object> same = Map.of("name", "x", "priority", 1);

        assertThat(AuditDiffs.diff(same, same)).isEmpty();
    }

    @Test
    void nullValues_normalizedInsteadOfThrowing() {
        Map<String, Object> before = new java.util.HashMap<>();
        before.put("baseUrl", null);
        Map<String, Object> after = new java.util.HashMap<>();
        after.put("baseUrl", "https://example.com");

        Map<String, Object> diff = AuditDiffs.diff(before, after);

        assertThat(diff.get("baseUrl")).isEqualTo(Map.of("old", "null", "new", "https://example.com"));
    }
}
