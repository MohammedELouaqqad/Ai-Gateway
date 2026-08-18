package com.backend.Ai_Gateway.services.guardrails;

/** Résultat d'une opération rate-limit store. */
public enum StoreOutcome {
    ALLOWED,
    BLOCKED,
    /** Redis injoignable — le caller applique fail-open / fail-closed */
    UNAVAILABLE
}
