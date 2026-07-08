package com.backend.Ai_Gateway.services;


import com.backend.Ai_Gateway.exceptions.RessourceAlreadyExistsException;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.repositories.ProviderRepository;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import com.backend.Ai_Gateway.services.dto.UpdateProviderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Provider createProvider(Provider newProvider) {
        boolean providerExists = providerRepository.existsByName(newProvider.getName());
        if (providerExists) {
            throw new RessourceAlreadyExistsException("Provider already exist");
        }
        return providerRepository.save(newProvider);
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

        provider.setName(newName);
        if (request.baseUrl() != null) {
            provider.setBaseUrl(request.baseUrl().trim());
        }
        if (request.priority() != null) {
            provider.setPriority(request.priority());
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
        provider.setEnabled(!provider.isEnabled());
        return providerRepository.save(provider);
    }
}
