package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.repositories.ProviderRepository;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import com.backend.Ai_Gateway.services.dto.CreateProviderRequest;
import com.backend.Ai_Gateway.services.dto.UpdateProviderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {

    @Mock ProviderRepository providerRepository;
    @Mock ModelRepository modelRepository;
    @Mock UsageLogRepository usageLogRepository;

    private ProviderService service;

    @BeforeEach
    void setUp() {
        service = new ProviderService(providerRepository, modelRepository, usageLogRepository);
        lenient().when(providerRepository.save(any(Provider.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createProvider_withApiKey_storesIt() {
        when(providerRepository.existsByName("Azure Prod")).thenReturn(false);
        CreateProviderRequest request = new CreateProviderRequest(
                "Azure Prod", ProviderType.AZURE_OPENAI, "https://my-resource.openai.azure.com",
                ProviderLocation.CLOUD, 1, true, "azure-secret");

        Provider created = service.createProvider(request);

        assertThat(created.getApiKey()).isEqualTo("azure-secret");
        assertThat(created.isApiKeyPresent()).isTrue();
    }

    @Test
    void createProvider_withoutApiKey_leavesItUnset() {
        when(providerRepository.existsByName("vLLM Local")).thenReturn(false);
        CreateProviderRequest request = new CreateProviderRequest(
                "vLLM Local", ProviderType.VLLM, "http://localhost:8000",
                ProviderLocation.LOCAL, 1, true, null);

        Provider created = service.createProvider(request);

        assertThat(created.getApiKey()).isNull();
        assertThat(created.isApiKeyPresent()).isFalse();
    }

    /**
     * The regression this test exists to catch: a PUT with a blank/absent apiKey (exactly
     * what an untouched <input type="password"> submits) must NOT wipe a key that was already
     * configured — silently, with no way to notice until the next real provider call fails.
     */
    @Test
    void updateProvider_blankApiKey_doesNotClearExistingKey() {
        Provider existing = existingAzureProvider();
        when(providerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(providerRepository.findByName("Azure Prod")).thenReturn(Optional.of(existing));

        UpdateProviderRequest request = new UpdateProviderRequest("Azure Prod", null, null, 2, "");

        Provider updated = service.updateProvider(1L, request);

        assertThat(updated.getApiKey()).isEqualTo("azure-secret");
        assertThat(updated.getPriority()).isEqualTo(2);
    }

    @Test
    void updateProvider_nullApiKey_doesNotClearExistingKey() {
        Provider existing = existingAzureProvider();
        when(providerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(providerRepository.findByName("Azure Prod")).thenReturn(Optional.of(existing));

        UpdateProviderRequest request = new UpdateProviderRequest("Azure Prod", null, null, null, null);

        Provider updated = service.updateProvider(1L, request);

        assertThat(updated.getApiKey()).isEqualTo("azure-secret");
    }

    @Test
    void updateProvider_nonBlankApiKey_replacesExistingKey() {
        Provider existing = existingAzureProvider();
        when(providerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(providerRepository.findByName("Azure Prod")).thenReturn(Optional.of(existing));

        UpdateProviderRequest request = new UpdateProviderRequest("Azure Prod", null, null, null, "new-secret");

        Provider updated = service.updateProvider(1L, request);

        assertThat(updated.getApiKey()).isEqualTo("new-secret");
    }

    @Test
    void apiKey_neverSerializedToJson_onlyPresenceFlagIs() throws Exception {
        Provider provider = existingAzureProvider();

        String json = new ObjectMapper().writeValueAsString(provider);

        assertThat(json).doesNotContain("azure-secret").doesNotContain("\"apiKey\"");
        assertThat(json).contains("\"apiKeyPresent\":true");
    }

    private static Provider existingAzureProvider() {
        Provider provider = new Provider();
        provider.setId(1L);
        provider.setName("Azure Prod");
        provider.setType(ProviderType.AZURE_OPENAI);
        provider.setApiKey("azure-secret");
        provider.setPriority(1);
        return provider;
    }
}
