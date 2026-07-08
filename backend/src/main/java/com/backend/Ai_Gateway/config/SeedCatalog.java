package com.backend.Ai_Gateway.config;

import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.enums.ProviderType;

import java.util.List;

/**
 * Catalogue de référence aligné sur les types supportés par le gateway (ProviderType).
 * Tous les éléments sont créés inactifs : l'admin les active manuellement.
 */
public final class SeedCatalog {

    private SeedCatalog() {}

    public record ProviderSeed(
            String name,
            String baseUrl,
            ProviderType type,
            ProviderLocation location,
            int priority
    ) {}

    public record ModelSeed(
            String displayName,
            String name,
            String providerName
    ) {}

    public static final List<ProviderSeed> PROVIDERS = List.of(
            new ProviderSeed("Ollama Local", "http://localhost:11434", ProviderType.OLLAMA, ProviderLocation.LOCAL, 1),
            new ProviderSeed("OpenAI", "https://api.openai.com/v1", ProviderType.OPENAI, ProviderLocation.CLOUD, 2),
            new ProviderSeed("Azure OpenAI", "https://your-resource.openai.azure.com", ProviderType.AZURE_OPENAI, ProviderLocation.CLOUD, 3),
            new ProviderSeed("SGLang", "http://localhost:30000", ProviderType.SGLANG, ProviderLocation.LOCAL, 4),
            new ProviderSeed("vLLM", "http://localhost:8000/v1", ProviderType.VLLM, ProviderLocation.LOCAL, 5)
    );

    public static final List<ModelSeed> MODELS = List.of(
            new ModelSeed("Qwen 0.5B", "qwen2.5:0.5b", "Ollama Local"),
            new ModelSeed("Llama 3.2 1B", "llama3.2:1b", "Ollama Local"),
            new ModelSeed("GPT-4o mini", "gpt-4o-mini", "OpenAI"),
            new ModelSeed("GPT-4o", "gpt-4o", "OpenAI"),
            new ModelSeed("GPT-4o (Azure)", "gpt-4o-azure", "Azure OpenAI"),
            new ModelSeed("Llama 3 8B Instruct", "meta-llama/Meta-Llama-3-8B-Instruct", "SGLang"),
            new ModelSeed("Mistral 7B Instruct", "mistral-7b-instruct", "vLLM")
    );
}
