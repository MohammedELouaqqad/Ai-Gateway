package com.backend.Ai_Gateway.config;

import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.repositories.ProviderRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final ModelRepository modelRepository;

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${app.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${OPENAI_API_KEY:}")
    private String openAiApiKey;

    public DevDataSeeder(
            UserRepository userRepository,
            ProviderRepository providerRepository,
            ModelRepository modelRepository
    ) {
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
        this.modelRepository = modelRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }

        User user = userRepository.findById(1L).orElseGet(() -> {
            User devUser = new User();
            devUser.setKeycloakId("kc-dev-1");
            devUser.setUsername("devuser");
            devUser.setEmail("dev@ai-gateway.local");
            devUser.setEnabled(true);
            devUser.setCreatedAt(Instant.now());
            User saved = userRepository.save(devUser);
            log.info("Seed: utilisateur de dev créé (id={})", saved.getId());
            return saved;
        });

        Map<String, Provider> providersByName = new HashMap<>();
        for (SeedCatalog.ProviderSeed seed : SeedCatalog.PROVIDERS) {
            Provider provider = providerRepository.findByName(seed.name())
                    .orElseGet(() -> {
                        Provider created = new Provider();
                        created.setName(seed.name());
                        created.setBaseUrl(resolveBaseUrl(seed));
                        created.setType(seed.type());
                        created.setLocation(seed.location());
                        created.setEnabled(false);
                        created.setPriority(seed.priority());
                        created.setCreatedAt(Instant.now());
                        Provider saved = providerRepository.save(created);
                        log.info("Seed: provider {} créé (inactif, id={})", saved.getName(), saved.getId());
                        return saved;
                    });
            providersByName.put(provider.getName(), provider);
        }

        applyOpenAiApiKeyFromEnv(providersByName);

        for (SeedCatalog.ModelSeed seed : SeedCatalog.MODELS) {
            if (modelRepository.findByName(seed.name()).isPresent()) {
                continue;
            }
            Provider provider = providersByName.get(seed.providerName());
            if (provider == null) {
                log.warn("Seed: provider {} introuvable pour le modèle {}", seed.providerName(), seed.name());
                continue;
            }
            Model model = new Model();
            model.setDisplayName(seed.displayName());
            model.setName(seed.name());
            model.setEnabled(false);
            model.setDefault(false);
            model.setCreatedAt(Instant.now());
            model.setProvider(provider);
            Model saved = modelRepository.save(model);
            log.info("Seed: modèle {} créé (inactif, id={})", saved.getName(), saved.getId());
        }

        activatePreferredOllamaModel(providersByName);

        if (user.getId() != 1L) {
            log.warn("Seed: l'utilisateur de dev n'a pas l'id 1 (id={}). Les controllers dev utilisent userId=1.", user.getId());
        }
    }

    /**
     * Active Ollama + {@link SeedCatalog#PREFERRED_OLLAMA_MODEL} comme défaut,
     * et désactive les anciens modèles Ollama absents du catalogue (ex. qwen).
     */
    private void activatePreferredOllamaModel(Map<String, Provider> providersByName) {
        Provider ollama = providersByName.get("Ollama Local");
        if (ollama == null) {
            ollama = providerRepository.findByName("Ollama Local").orElse(null);
        }
        if (ollama == null) {
            log.warn("Seed: provider Ollama Local introuvable, modèle préféré non activé");
            return;
        }
        if (!ollama.isEnabled()) {
            ollama.setEnabled(true);
            providerRepository.save(ollama);
            log.info("Seed: provider Ollama Local activé");
        }

        Model preferred = modelRepository.findByName(SeedCatalog.PREFERRED_OLLAMA_MODEL).orElse(null);
        if (preferred == null) {
            log.warn("Seed: modèle {} introuvable", SeedCatalog.PREFERRED_OLLAMA_MODEL);
            return;
        }

        for (Model m : modelRepository.findAll()) {
            boolean isPreferred = SeedCatalog.PREFERRED_OLLAMA_MODEL.equals(m.getName());
            boolean isOllama = m.getProvider() != null
                    && m.getProvider().getType() == com.backend.Ai_Gateway.enums.ProviderType.OLLAMA;
            if (isPreferred) {
                m.setEnabled(true);
                m.setDefault(true);
                m.setProvider(ollama);
                modelRepository.save(m);
            } else {
                if (m.isDefault()) {
                    m.setDefault(false);
                    modelRepository.save(m);
                }
                // Anciens tags Ollama hors catalogue (ex. qwen2.5:0.5b) → inactifs
                if (isOllama && SeedCatalog.MODELS.stream().noneMatch(s -> s.name().equals(m.getName()))) {
                    if (m.isEnabled()) {
                        m.setEnabled(false);
                        modelRepository.save(m);
                        log.info("Seed: ancien modèle Ollama désactivé ({})", m.getName());
                    }
                }
            }
        }
        log.info("Seed: modèle par défaut = {}", SeedCatalog.PREFERRED_OLLAMA_MODEL);
    }

    private String resolveBaseUrl(SeedCatalog.ProviderSeed seed) {
        if (seed.type() == com.backend.Ai_Gateway.enums.ProviderType.OLLAMA) {
            return ollamaBaseUrl;
        }
        return seed.baseUrl();
    }

    private void applyOpenAiApiKeyFromEnv(Map<String, Provider> providersByName) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            return;
        }
        Provider openAi = providersByName.get("OpenAI");
        if (openAi == null) {
            openAi = providerRepository.findByName("OpenAI").orElse(null);
        }
        if (openAi == null) {
            log.warn("Seed: provider OpenAI introuvable, clé OPENAI_API_KEY non appliquée");
            return;
        }
        openAi.setApiKey(openAiApiKey.trim());
        providerRepository.save(openAi);
        log.info("Seed: clé OpenAI synchronisée depuis OPENAI_API_KEY (non journalisée)");
    }
}
