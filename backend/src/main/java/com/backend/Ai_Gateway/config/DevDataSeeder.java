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

        if (user.getId() != 1L) {
            log.warn("Seed: l'utilisateur de dev n'a pas l'id 1 (id={}). Les controllers dev utilisent userId=1.", user.getId());
        }
    }

    private String resolveBaseUrl(SeedCatalog.ProviderSeed seed) {
        if (seed.type() == com.backend.Ai_Gateway.enums.ProviderType.OLLAMA) {
            return ollamaBaseUrl;
        }
        return seed.baseUrl();
    }
}
