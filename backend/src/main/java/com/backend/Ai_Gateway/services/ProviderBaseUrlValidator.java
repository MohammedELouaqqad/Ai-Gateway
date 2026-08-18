package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.enums.ProviderType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * {@code baseUrl} is admin-only-writable (see {@code ProviderController}'s
 * {@code @PreAuthorize("hasRole('ADMIN')")}), so this is defense-in-depth against a
 * careless/compromised admin session, not a primary trust boundary — but the gateway forwards
 * the provider's own API key as an {@code Authorization}/{@code api-key} header to whatever
 * host {@code baseUrl} points at, so an unvalidated value is both an SSRF vector and a
 * key-exfiltration vector.
 *
 * <p>Cloud families ({@link ProviderType#OPENAI}/{@link ProviderType#AZURE_OPENAI}) must be
 * reached over {@code https} — there is never a legitimate reason for them to point at
 * localhost, a private range, or plain http. Self-hosted families
 * ({@link ProviderType#OLLAMA}/{@link ProviderType#VLLM}/{@link ProviderType#SGLANG}) are
 * deliberately NOT restricted to public hosts or https — pointing at a LAN/private-network
 * instance (e.g. {@code http://host.docker.internal:11434}) is their entire purpose. The cloud
 * metadata endpoint is blocked for every family regardless, since no provider legitimately
 * needs it.
 *
 * <p><b>Known residual gap</b>: the metadata check is a literal string match on
 * {@link URI#getHost()} as written by the admin — it does NOT resolve DNS, so a hostname that
 * resolves to {@code 169.254.169.254} (including via DNS rebinding after this check ran), or
 * an alternate IP encoding (decimal/octal/hex, e.g. {@code 2852039166}), bypasses it. Closing
 * that fully requires resolving the host and checking the resolved IP at each outbound call
 * (not just at admin-write time), which is real added latency/complexity for an admin-only
 * surface — accepted as-is for now given the defense-in-depth framing above, not closed.
 */
public final class ProviderBaseUrlValidator {

    private static final Set<ProviderType> CLOUD_FAMILIES = Set.of(ProviderType.OPENAI, ProviderType.AZURE_OPENAI);

    /** AWS/Azure/GCP instance metadata service — the classic SSRF-to-credential-theft target. */
    private static final String METADATA_HOST = "169.254.169.254";

    private ProviderBaseUrlValidator() {}

    /** @throws IllegalArgumentException if {@code baseUrl} is unsafe for this provider family. */
    public static void validate(ProviderType type, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return; // unset → the family's own defaultBaseUrl() is used, nothing to validate
        }
        URI uri;
        try {
            uri = new URI(baseUrl.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("baseUrl: not a valid URL");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("baseUrl: must be an absolute URL with a host (e.g. https://api.example.com)");
        }
        if (METADATA_HOST.equals(host)) {
            throw new IllegalArgumentException("baseUrl: cloud metadata endpoint is not allowed");
        }
        if (CLOUD_FAMILIES.contains(type) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("baseUrl: must use https:// for this provider type");
        }
    }
}
