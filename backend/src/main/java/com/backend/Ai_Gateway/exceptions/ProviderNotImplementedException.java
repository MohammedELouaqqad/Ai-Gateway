package com.backend.Ai_Gateway.exceptions;

import com.backend.Ai_Gateway.enums.ProviderType;

/**
 * Levée quand le gateway n'a pas d'adaptateur pour un {@link ProviderType}
 * (le provider distant peut pourtant être capable de chat/stream).
 */
public class ProviderNotImplementedException extends RuntimeException {

    private final ProviderType providerType;

    public ProviderNotImplementedException(ProviderType providerType) {
        super("Provider " + providerType + " non implémenté dans ce gateway"
                + " (adaptateur manquant : DTO + mapper)");
        this.providerType = providerType;
    }

    public ProviderType getProviderType() {
        return providerType;
    }
}
