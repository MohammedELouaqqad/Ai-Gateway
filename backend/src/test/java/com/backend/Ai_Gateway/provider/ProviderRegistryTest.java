package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.exceptions.ProviderNotImplementedException;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderRegistryTest {

    @Test
    void get_dispatchesToRegisteredProvider() {
        LLMProvider ollama = new FakeProvider(Set.of(ProviderType.OLLAMA));
        LLMProvider openai = new FakeProvider(Set.of(ProviderType.OPENAI, ProviderType.AZURE_OPENAI));
        ProviderRegistry registry = new ProviderRegistry(List.of(ollama, openai));

        assertThat(registry.supports(ProviderType.OLLAMA)).isTrue();
        assertThat(registry.supports(ProviderType.OPENAI)).isTrue();
        assertThat(registry.supports(ProviderType.AZURE_OPENAI)).isTrue();
        assertThat(registry.get(ProviderType.OPENAI)).isSameAs(openai);
        assertThat(registry.get(ProviderType.AZURE_OPENAI)).isSameAs(openai);
    }

    @Test
    void get_unknownTypeThrowsProviderNotImplemented() {
        ProviderRegistry registry = new ProviderRegistry(List.of(new FakeProvider(Set.of(ProviderType.OLLAMA))));

        assertThat(registry.supports(ProviderType.VLLM)).isFalse();
        assertThatThrownBy(() -> registry.get(ProviderType.VLLM))
                .isInstanceOf(ProviderNotImplementedException.class);
    }

    @Test
    void duplicateTypeAcrossBeansFailsFast() {
        LLMProvider a = new FakeProvider(Set.of(ProviderType.OPENAI));
        LLMProvider b = new FakeProvider(Set.of(ProviderType.OPENAI));

        assertThatThrownBy(() -> new ProviderRegistry(List.of(a, b)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple LLMProvider beans for type OPENAI");
    }

    private record FakeProvider(Set<ProviderType> types) implements LLMProvider {
        @Override
        public ChatResponse chat(com.backend.Ai_Gateway.entity.Provider provider, ChatRequest request) {
            return new ChatResponse("ok", 0, 0);
        }

        @Override
        public Flux<ChatChunk> stream(com.backend.Ai_Gateway.entity.Provider provider, ChatRequest request) {
            return Flux.empty();
        }
    }
}
