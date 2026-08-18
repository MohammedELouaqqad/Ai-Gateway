package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.ApiKey;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.repositories.ApiKeyRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.ApiKeyCreatedView;
import com.backend.Ai_Gateway.services.dto.ApiKeyView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {

    private static final String KEY_PREFIX_TAG = "sk-";
    private static final int SECRET_BYTES = 32;
    private static final int DISPLAY_PREFIX_LEN = 12;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder RAW_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository, UserRepository userRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
    }

    /** {@code expiresInDays} is optional — null means the key never expires. */
    @Transactional
    public ApiKeyCreatedView generate(Long userId, String name, Integer expiresInDays) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RessourceNotFoundException("User not found"));

        String rawKey = KEY_PREFIX_TAG + randomSecret();

        ApiKey apiKey = new ApiKey();
        apiKey.setUser(user);
        apiKey.setName(name.trim());
        apiKey.setKeyHash(sha256Hex(rawKey));
        apiKey.setKeyPrefix(rawKey.substring(0, Math.min(DISPLAY_PREFIX_LEN, rawKey.length())));
        apiKey.setEnabled(true);
        apiKey.setCreatedAt(Instant.now());
        apiKey.setExpiresAt(expiresInDays == null ? null : Instant.now().plus(expiresInDays, ChronoUnit.DAYS));

        ApiKey saved = apiKeyRepository.save(apiKey);
        return ApiKeyCreatedView.from(saved, rawKey);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyView> list(Long userId) {
        return apiKeyRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(ApiKeyView::from)
                .toList();
    }

    @Transactional
    public ApiKeyView revoke(Long userId, Long apiKeyId) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new RessourceNotFoundException("API key not found"));
        if (!apiKey.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Vous n'êtes pas propriétaire de cette clé API");
        }
        apiKey.setEnabled(false);
        return ApiKeyView.from(apiKeyRepository.save(apiKey));
    }

    /**
     * Used by the {@code /v1/**} auth filter — returns the owning key only if the key itself
     * is enabled, unexpired, AND its owning user hasn't been disabled by an admin (otherwise
     * disabling a user would leave their API keys as a backdoor around that decision).
     */
    @Transactional(readOnly = true)
    public Optional<ApiKey> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        return apiKeyRepository.findByKeyHash(sha256Hex(rawKey))
                .filter(ApiKey::isEnabled)
                .filter(apiKey -> apiKey.getExpiresAt() == null || apiKey.getExpiresAt().isAfter(Instant.now()))
                .filter(apiKey -> apiKey.getUser() != null && apiKey.getUser().isEnabled());
    }

    private static String randomSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return RAW_ENCODER.encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
