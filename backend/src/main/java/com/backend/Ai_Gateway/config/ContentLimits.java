package com.backend.Ai_Gateway.config;

/**
 * Limites de taille pour éviter les payloads abusifs / context overflow.
 */
public final class ContentLimits {

    private ContentLimits() {}

    /** Nom de template */
    public static final int TEMPLATE_NAME_MAX = 100;

    /** Description courte (pas le prompt) */
    public static final int TEMPLATE_DESCRIPTION_MAX = 500;

    /** Corps du template (prompt réutilisable) */
    public static final int TEMPLATE_CONTENT_MAX = 20_000;

    /** Message chat utilisateur (après insertion template + saisie) */
    public static final int CHAT_MESSAGE_MAX = 50_000;

    /** Seuil d'avertissement UI (~80 % de CHAT_MESSAGE_MAX) */
    public static final int CHAT_MESSAGE_WARN = 40_000;

    /**
     * Budget caractères pour l'historique envoyé au LLM.
     * Approximation grossière : ~4 caractères ≈ 1 token → ~30k tokens.
     * Les messages les plus anciens sont tronqués en premier.
     */
    public static final int CHAT_CONTEXT_CHAR_BUDGET = 120_000;

    /** Nom d'une clé API */
    public static final int API_KEY_NAME_MAX = 100;

    /** Nombre max de messages dans une requête /v1/chat/completions (l'appelant fournit tout l'historique). */
    public static final int V1_MESSAGES_MAX_COUNT = 200;

    /** Taille max du corps d'une requête /v1/** — rejetée avant désérialisation Jackson (voir MaxRequestSizeFilter). */
    public static final long V1_REQUEST_BODY_MAX_BYTES = 20_000_000L;
}
