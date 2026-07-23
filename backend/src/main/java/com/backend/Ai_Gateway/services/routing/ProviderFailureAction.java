package com.backend.Ai_Gateway.services.routing;

/**
 * Classification des erreurs provider pour retry / fallback.
 * Ne jamais retry après facturation probable (chunks reçus / timeout mid-stream).
 */
public enum ProviderFailureAction {
    /** Connexion / DNS / timeout avant octets / 5xx → retry 1× même modèle */
    RETRY_SAME,
    /** 429 ou provider indisponible → candidat suivant de ranked */
    FALLBACK_NEXT,
    /** 4xx prompt / auth / déjà streamé → remonter l'erreur */
    FAIL
}
