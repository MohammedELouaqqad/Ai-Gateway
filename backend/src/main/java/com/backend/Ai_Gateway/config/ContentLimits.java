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
}
