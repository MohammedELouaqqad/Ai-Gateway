package com.backend.Ai_Gateway.services;


import com.backend.Ai_Gateway.exceptions.RessourceAlreadyExistsException;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.repositories.ProviderRepository;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import com.backend.Ai_Gateway.services.dto.CreateProviderRequest;
import com.backend.Ai_Gateway.services.dto.UpdateProviderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ProviderService {

    private final ProviderRepository providerRepository;
    private final ModelRepository modelRepository;
    private final UsageLogRepository usageLogRepository;

    public ProviderService(
            ProviderRepository providerRepository,
            ModelRepository modelRepository,
            UsageLogRepository usageLogRepository
    ) {
        this.providerRepository = providerRepository;
        this.modelRepository = modelRepository;
        this.usageLogRepository = usageLogRepository;
    }

    public List<Provider> getAllProviders() {
        return providerRepository.findAll();
    }

    public Provider getProviderById(Long id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new RessourceNotFoundException("provider not found with id " + id));
    }

    @Transactional
    public Provider createProvider(CreateProviderRequest request) {
        String name = request.name().trim();
        if (providerRepository.existsByName(name)) {
            throw new RessourceAlreadyExistsException("Provider already exist");
        }

        ProviderBaseUrlValidator.validate(request.type(), request.baseUrl());

        Provider provider = new Provider();
        provider.setName(name);
        provider.setType(request.type());
        if (request.baseUrl() != null && !request.baseUrl().isBlank()) {
            provider.setBaseUrl(request.baseUrl().trim());
        }
        provider.setLocation(request.location());
        provider.setPriority(request.priority());
        provider.setEnabled(request.enabled());
        provider.setCreatedAt(Instant.now());
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            provider.setApiKey(request.apiKey().trim());
        }
        return providerRepository.save(provider);
    }

    @Transactional
    public Provider updateProvider(Long id, UpdateProviderRequest request) {
        Provider provider = getProviderById(id);
        String newName = request.name().trim();

        providerRepository.findByName(newName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new RessourceAlreadyExistsException("Provider already exists: " + newName);
                });

        ProviderBaseUrlValidator.validate(provider.getType(), request.baseUrl());

        provider.setName(newName);
        if (request.baseUrl() != null) {
            provider.setBaseUrl(request.baseUrl().trim());
        }
        if (request.location() != null) {
            provider.setLocation(request.location());
        }
        if (request.priority() != null) {
            provider.setPriority(request.priority());
        }
        // Blank/null = leave the stored key untouched — see UpdateProviderRequest javadoc for why.
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            provider.setApiKey(request.apiKey().trim());
        }
        return providerRepository.save(provider);
    }

    @Transactional
    public void deleteProvider(Long id) {
        Provider provider = getProviderById(id);
        if (modelRepository.existsByProvider_Id(id)) {
            throw new IllegalArgumentException("Provider has linked models");
        }
        if (usageLogRepository.existsByProvider_Id(id)) {
            throw new IllegalArgumentException("Provider is used in usage logs");
        }
        providerRepository.delete(provider);
    }

    @Transactional
    public Provider changeStatus(Long id) {
        Provider provider = getProviderById(id);
        boolean enabling = !provider.isEnabled();
        provider.setEnabled(enabling);
        Provider saved = providerRepository.save(provider);

        if (!enabling) {
            for (var model : modelRepository.findByProvider_Id(id)) {
                boolean changed = false;
                if (model.isEnabled()) {
                    model.setEnabled(false);
                    changed = true;
                }
                if (model.isDefault()) {
                    model.setDefault(false);
                    changed = true;
                }
                if (changed) {
                    modelRepository.save(model);
                }
            }
        }
        return saved;
    }
}
