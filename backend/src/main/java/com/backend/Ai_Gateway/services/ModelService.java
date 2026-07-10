package com.backend.Ai_Gateway.services;


import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.exceptions.RessourceAlreadyExistsException;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.repositories.ProviderRepository;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import com.backend.Ai_Gateway.services.dto.CreateModelRequest;
import com.backend.Ai_Gateway.services.dto.UpdateModelRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ModelService {

    private final ModelRepository modelRepository;
    private final ProviderRepository providerRepository;
    private final ConversationRepository conversationRepository;
    private final UsageLogRepository usageLogRepository;

    public ModelService(
            ModelRepository modelRepository,
            ProviderRepository providerRepository,
            ConversationRepository conversationRepository,
            UsageLogRepository usageLogRepository
    ) {
        this.modelRepository = modelRepository;
        this.providerRepository = providerRepository;
        this.conversationRepository = conversationRepository;
        this.usageLogRepository = usageLogRepository;
    }

    public List<Model> getAllModels() {
        return modelRepository.findAll();
    }

    public Model getById(Long id) {
        return modelRepository.findById(id)
                .orElseThrow(() -> new RessourceNotFoundException("Model not found with id " + id));
    }

    public Model resolveActiveModel(String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            Model model = modelRepository.findByName(modelName.trim())
                    .orElseThrow(() -> new RessourceNotFoundException("Model not found: " + modelName));
            ensureModelUsable(model);
            return model;
        }

        Model defaultModel = modelRepository.findByIsDefaultTrue()
                .orElseThrow(() -> new RessourceNotFoundException("No default model configured"));
        ensureModelUsable(defaultModel);
        return defaultModel;
    }

    private void ensureModelUsable(Model model) {
        if (!model.isEnabled()) {
            throw new IllegalArgumentException("Model is disabled: " + model.getName());
        }
        var provider = model.getProvider();
        if (provider == null) {
            throw new IllegalArgumentException("Model has no provider: " + model.getName());
        }
        if (!provider.isEnabled()) {
            throw new IllegalArgumentException(
                    "Provider is disabled: " + provider.getName() + ". Activez le fournisseur avant d'utiliser ce modèle."
            );
        }
    }

    @Transactional
    public Model createModel(CreateModelRequest request) {
        if (modelRepository.findByName(request.name().trim()).isPresent()) {
            throw new RessourceAlreadyExistsException("Model already exists: " + request.name());
        }

        var provider = providerRepository.findById(request.providerId())
                .orElseThrow(() -> new RessourceNotFoundException("Provider not found with id " + request.providerId()));

        if (request.isDefault()) {
            clearDefaultModel();
        }

        Model model = new Model();
        model.setDisplayName(request.displayName().trim());
        model.setName(request.name().trim());
        model.setEnabled(request.enabled());
        model.setDefault(request.isDefault());
        model.setCreatedAt(Instant.now());
        model.setProvider(provider);
        return modelRepository.save(model);
    }

    @Transactional
    public Model updateModel(Long id, UpdateModelRequest request) {
        Model model = getById(id);
        String newName = request.name().trim();

        modelRepository.findByName(newName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new RessourceAlreadyExistsException("Model already exists: " + newName);
                });

        model.setDisplayName(request.displayName().trim());
        model.setName(newName);
        return modelRepository.save(model);
    }

    @Transactional
    public void deleteModel(Long id) {
        Model model = getById(id);
        if (model.isDefault()) {
            throw new IllegalArgumentException("Cannot delete the default model");
        }
        if (conversationRepository.existsByModel_Id(id)) {
            throw new IllegalArgumentException("Model is used in conversations");
        }
        if (usageLogRepository.existsByModel_Id(id)) {
            throw new IllegalArgumentException("Model is used in usage logs");
        }
        modelRepository.delete(model);
    }

    @Transactional
    public Model changeStatus(Long id) {
        Model model = getById(id);
        boolean enabling = !model.isEnabled();
        if (enabling) {
            var provider = model.getProvider();
            if (provider == null || !provider.isEnabled()) {
                String providerName = provider != null ? provider.getName() : "inconnu";
                throw new IllegalArgumentException(
                        "Impossible d'activer le modèle : le fournisseur « " + providerName + " » est inactif."
                );
            }
        }
        model.setEnabled(enabling);
        if (!model.isEnabled() && model.isDefault()) {
            model.setDefault(false);
        }
        return modelRepository.save(model);
    }

    @Transactional
    public Model defaultModel(Long id) {
        Model model = getById(id);
        ensureModelUsable(model);
        clearDefaultModel();
        model.setDefault(true);
        return modelRepository.save(model);
    }

    private void clearDefaultModel() {
        Optional<Model> currentDefault = modelRepository.findByIsDefaultTrue();
        currentDefault.ifPresent(model -> {
            model.setDefault(false);
            modelRepository.save(model);
        });
    }
}
