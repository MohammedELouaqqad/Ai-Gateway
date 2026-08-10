package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.ApiKey;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.repositories.ApiKeyRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.ApiKeyCreatedView;
import com.backend.Ai_Gateway.services.dto.ApiKeyView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock ApiKeyRepository apiKeyRepository;
    @Mock UserRepository userRepository;

    private ApiKeyService service;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new ApiKeyService(apiKeyRepository, userRepository);
        owner = new User();
        owner.setId(1L);
        owner.setEnabled(true);
    }

    @Test
    void generate_returnsRawKeyOnceAndPersistsOnlyItsHash() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> {
            ApiKey saved = inv.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        ApiKeyCreatedView created = service.generate(1L, "ci-key", null);

        assertThat(created.rawKey()).startsWith("sk-");
        assertThat(created.keyPrefix()).isEqualTo(created.rawKey().substring(0, 12));
        assertThat(created.enabled()).isTrue();
        assertThat(created.expiresAt()).isNull();

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey persisted = captor.getValue();
        assertThat(persisted.getKeyHash()).isNotEqualTo(created.rawKey());
        assertThat(persisted.getKeyHash()).hasSize(64); // hex-encoded SHA-256
        assertThat(persisted.getUser()).isSameAs(owner);
        assertThat(persisted.getExpiresAt()).isNull();
    }

    @Test
    void generate_withExpiresInDays_setsExpiresAtInTheFuture() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyCreatedView created = service.generate(1L, "ci-key", 30);

        assertThat(created.expiresAt()).isAfter(Instant.now().plusSeconds(29 * 24 * 3600));
        assertThat(created.expiresAt()).isBefore(Instant.now().plusSeconds(31 * 24 * 3600));
    }

    @Test
    void generate_unknownUserThrows() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RessourceNotFoundException.class, () -> service.generate(99L, "x", null));
    }

    @Test
    void authenticate_returnsEmptyForBlankOrUnknownKey() {
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("")).isEmpty();

        when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.empty());
        assertThat(service.authenticate("sk-does-not-exist")).isEmpty();
    }

    @Test
    void authenticate_rejectsDisabledKey() {
        ApiKey disabled = apiKeyEntity(false);
        when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.of(disabled));

        assertThat(service.authenticate("sk-some-raw-key")).isEmpty();
    }

    @Test
    void authenticate_acceptsEnabledKey() {
        ApiKey enabled = apiKeyEntity(true);
        when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.of(enabled));

        assertThat(service.authenticate("sk-some-raw-key")).contains(enabled);
    }

    @Test
    void authenticate_rejectsExpiredKey() {
        ApiKey expired = apiKeyEntity(true);
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.of(expired));

        assertThat(service.authenticate("sk-some-raw-key")).isEmpty();
    }

    @Test
    void authenticate_acceptsKeyWithFutureExpiry() {
        ApiKey notYetExpired = apiKeyEntity(true);
        notYetExpired.setExpiresAt(Instant.now().plusSeconds(3600));
        when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.of(notYetExpired));

        assertThat(service.authenticate("sk-some-raw-key")).contains(notYetExpired);
    }

    @Test
    void authenticate_rejectsKeyWhoseOwnerIsDisabled() {
        User disabledOwner = new User();
        disabledOwner.setId(2L);
        disabledOwner.setEnabled(false);
        ApiKey key = new ApiKey();
        key.setUser(disabledOwner);
        key.setEnabled(true);
        when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.of(key));

        assertThat(service.authenticate("sk-some-raw-key")).isEmpty();
    }

    @Test
    void revoke_ownerCanDisableTheirOwnKey() {
        ApiKey key = apiKeyEntity(true);
        key.setId(5L);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyView view = service.revoke(1L, 5L);

        assertThat(view.enabled()).isFalse();
        assertThat(key.isEnabled()).isFalse();
    }

    @Test
    void revoke_nonOwnerIsRejected() {
        ApiKey key = apiKeyEntity(true);
        key.setId(5L);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        assertThrows(UnauthorizedException.class, () -> service.revoke(2L, 5L));
    }

    @Test
    void list_returnsViewsOrderedByCreation() {
        ApiKey key = apiKeyEntity(true);
        key.setId(7L);
        when(apiKeyRepository.findAllByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(key));

        List<ApiKeyView> views = service.list(1L);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).id()).isEqualTo(7L);
    }

    private ApiKey apiKeyEntity(boolean enabled) {
        ApiKey apiKey = new ApiKey();
        apiKey.setUser(owner);
        apiKey.setName("ci-key");
        apiKey.setKeyHash("irrelevant-for-lookup-mocks");
        apiKey.setKeyPrefix("sk-abcd1234");
        apiKey.setEnabled(enabled);
        apiKey.setCreatedAt(Instant.now());
        return apiKey;
    }
}
