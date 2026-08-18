package com.backend.Ai_Gateway.services;

/**
 * Classification des erreurs provider pour la politique de retry de {@link ChatExecutor}.
 * Ne jamais retry après facturation probable (chunks reçus / timeout mid-stream).
 */
public enum ProviderFailureAction {
    /** Connexion / DNS / timeout avant octets / 5xx → retry 1× même modèle */
    RETRY_SAME,
    /** 429, provider indisponible, 4xx prompt/auth, ou déjà streamé → remonter l'erreur */
    FAIL
}
