package com.backend.Ai_Gateway.services;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderFailureClassifierTest {

    @Test
    void retriesConnectionAndTimeoutBeforeBytes() {
        assertThat(ProviderFailureClassifier.classify(new ConnectException("refused"), false))
                .isEqualTo(ProviderFailureAction.RETRY_SAME);
        assertThat(ProviderFailureClassifier.classify(new TimeoutException("timeout"), false))
                .isEqualTo(ProviderFailureAction.RETRY_SAME);
    }

    @Test
    void fallbackOn429() {
        WebClientResponseException ex = WebClientResponseException.create(
                429, "Too Many Requests", null, null, null);
        assertThat(ProviderFailureClassifier.classify(ex, false))
                .isEqualTo(ProviderFailureAction.FALLBACK_NEXT);
    }

    @Test
    void failsAfterBytesReceived() {
        assertThat(ProviderFailureClassifier.classify(new TimeoutException("mid"), true))
                .isEqualTo(ProviderFailureAction.FAIL);
    }

    @Test
    void failsOn4xx() {
        WebClientResponseException ex = WebClientResponseException.create(
                400, "Bad Request", null, null, null);
        assertThat(ProviderFailureClassifier.classify(ex, false))
                .isEqualTo(ProviderFailureAction.FAIL);
    }
}
