package com.backend.Ai_Gateway.services;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the {@code details} diff for an {@code AuditLog} entry: only the fields that actually
 * changed, each as {@code {"old": ..., "new": ...}}.
 *
 * <p><b>Secrets never enter this.</b> {@code before}/{@code after} are built by each caller from
 * scalar snapshots it controls — a secret field (e.g. a provider's API key) must never be put
 * into either map in the first place; compute a boolean like {@code apiKeyChanged} separately
 * and add it directly to the result instead. This function has no way to know which fields are
 * sensitive, so it can't redact after the fact — exclusion has to happen at the call site,
 * before the maps are built.
 */
public final class AuditDiffs {

    private AuditDiffs() {}

    public static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String key : before.keySet()) {
            Object oldValue = before.get(key);
            Object newValue = after.get(key);
            if (!Objects.equals(oldValue, newValue)) {
                changes.put(key, Map.of("old", nullSafe(oldValue), "new", nullSafe(newValue)));
            }
        }
        return changes;
    }

    /** {@code Map.of} rejects null values — normalize to a literal "null" marker instead. */
    private static Object nullSafe(Object value) {
        return value != null ? value : "null";
    }
}
