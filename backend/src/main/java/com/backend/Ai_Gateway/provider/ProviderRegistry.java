package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.exceptions.ProviderNotImplementedException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry mapping provider type -> adapter bean.
 */
@Component
public class ProviderRegistry {

    private final Map<ProviderType, LLMProvider> adapters;

    public ProviderRegistry(List<LLMProvider> providers) {
        EnumMap<ProviderType, LLMProvider> map = new EnumMap<>(ProviderType.class);
        for (LLMProvider provider : providers) {
            for (ProviderType type : provider.types()) {
                LLMProvider previous = map.put(type, provider);
                if (previous != null && previous != provider) {
                    throw new IllegalStateException(
                            "Multiple LLMProvider beans for type " + type + ": "
                                    + previous.getClass().getSimpleName() + " and "
                                    + provider.getClass().getSimpleName()
                    );
                }
            }
        }
        this.adapters = Map.copyOf(map);
    }

    public LLMProvider get(ProviderType type) {
        LLMProvider adapter = adapters.get(type);
        if (adapter == null) {
            throw new ProviderNotImplementedException(type);
        }
        return adapter;
    }

    public boolean supports(ProviderType type) {
        return adapters.containsKey(type);
    }
}
