package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.provider.dto.ProviderModelInfo;

import java.util.List;

/** Optional capability for providers exposing model listing. */
public interface ModelListingProvider {
    List<ProviderModelInfo> models();
}
