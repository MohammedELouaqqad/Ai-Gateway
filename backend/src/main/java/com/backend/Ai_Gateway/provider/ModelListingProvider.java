package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.provider.dto.ProviderModelInfo;

import java.util.List;

/**
 * Capacité optionnelle : lister les modèles réellement disponibles chez CETTE instance de
 * provider (baseUrl/clé propres à {@code provider}, comme {@link LLMProvider#chat}) — pas un
 * modèle unique par type, d'où le paramètre {@link Provider} (absent de la version d'origine,
 * jamais implémentée : sans lui impossible de savoir quelle instance interroger).
 */
public interface ModelListingProvider {
    List<ProviderModelInfo> models(Provider provider);
}
