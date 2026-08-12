package com.backend.Ai_Gateway.services;


import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.exceptions.RessourceAlreadyExistsException;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.provider.LLMProvider;
import com.backend.Ai_Gateway.provider.ModelListingProvider;
import com.backend.Ai_Gateway.provider.ProviderRegistry;
import com.backend.Ai_Gateway.provider.dto.ProviderModelInfo;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.repositories.ProviderRepository;
import com.backend.Ai_Gateway.repositories.UsageLogRepository;
import com.backend.Ai_Gateway.services.dto.CreateModelRequest;
import com.backend.Ai_Gateway.services.dto.UpdateModelRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ModelService {

    private static final Logger log = LoggerFactory.getLogger(ModelService.class);

    private final ModelRepository modelRepository;
    private final ProviderRepository providerRepository;
    private final ConversationRepository conversationRepository;
    private final UsageLogRepository usageLogRepository;
    private final ProviderRegistry providerRegistry;

    public ModelService(
            ModelRepository modelRepository,
            ProviderRepository providerRepository,
            ConversationRepository conversationRepository,
            UsageLogRepository usageLogRepository,
            ProviderRegistry providerRegistry
    ) {
        this.modelRepository = modelRepository;
        this.providerRepository = providerRepository;
        this.conversationRepository = conversationRepository;
        this.usageLogRepository = usageLogRepository;
        this.providerRegistry = providerRegistry;
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

        assertModelExistsOnProvider(provider, request.name().trim());

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

    /**
     * Refuse un {@code name} qui n'existe pas réellement chez le provider — évite de créer un
     * modèle qui échouera seulement au premier chat utilisateur (erreur 404 Ollama non
     * catégorisée jusqu'ici, voir GlobalExceptionHandler).
     * <p>
     * Best-effort, jamais bloquant pour une raison qui n'est pas "le nom est faux" :
     * <ul>
     *   <li>Provider dont l'adaptateur n'implémente pas {@link ModelListingProvider} — à ce
     *       jour seul Azure OpenAI (déploiements, pas de {@code /models} dans la même forme ;
     *       Ollama/OpenAI/vLLM/SGLang sont tous couverts) — aucune vérification possible, on
     *       laisse passer comme avant.</li>
     *   <li>Provider listable mais injoignable au moment de la création (ex. Ollama down) —
     *       on ne bloque pas la création pour une panne transitoire sans rapport avec le nom
     *       saisi ; le check de robustesse au chat (GlobalExceptionHandler) reste le filet.</li>
     * </ul>
     */
    private void assertModelExistsOnProvider(Provider provider, String modelName) {
        LLMProvider adapter = providerRegistry.get(provider.getType());
        if (!(adapter instanceof ModelListingProvider listing)) {
            return;
        }
        List<ProviderModelInfo> available;
        try {
            available = listing.models(provider);
        } catch (RuntimeException ex) {
            log.warn("Skipping model-existence check — provider '{}' unreachable: {}",
                    provider.getName(), ex.getMessage());
            return;
        }
        boolean exists = available.stream().anyMatch(m -> modelName.equals(m.name()));
        if (!exists) {
            String sample = available.stream().map(ProviderModelInfo::name).limit(5)
                    .reduce((a, b) -> a + ", " + b).orElse("aucun modèle disponible");
            throw new IllegalArgumentException(
                    "Modèle « " + modelName + " » introuvable chez « " + provider.getName()
                            + " ». Modèles disponibles : " + sample
            );
        }
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

        Provider targetProvider = model.getProvider();
        boolean providerChanged = model.getProvider() == null
                || !request.providerId().equals(model.getProvider().getId());
        if (providerChanged) {
            targetProvider = providerRepository.findById(request.providerId())
                    .orElseThrow(() -> new RessourceNotFoundException("Provider not found with id " + request.providerId()));
        }

        // Nom OU provider changé → revalider contre la CIBLE (pas l'ancien provider) : renommer
        // et changer de provider sont le même trou que la création sans check, même garde-fou.
        if (providerChanged || !newName.equals(model.getName())) {
            assertModelExistsOnProvider(targetProvider, newName);
        }

        model.setDisplayName(request.displayName().trim());
        model.setName(newName);
        model.setProvider(targetProvider);
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
