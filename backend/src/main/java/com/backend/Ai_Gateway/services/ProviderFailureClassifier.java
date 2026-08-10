package com.backend.Ai_Gateway.services;

import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

public final class ProviderFailureClassifier {

    private ProviderFailureClassifier() {}

    public static ProviderFailureAction classify(Throwable error, boolean anyBytesReceived) {
        if (anyBytesReceived) {
            return ProviderFailureAction.FAIL;
        }
        Throwable root = rootCause(error);
        if (root instanceof WebClientResponseException wcre) {
            int status = wcre.getStatusCode().value();
            if (status >= 500) {
                return ProviderFailureAction.RETRY_SAME;
            }
            // 429 (rate-limited) and 4xx (invalid prompt, auth, etc.) — no second candidate
            // to fall back to (single-model execution), so both end up the same: FAIL.
            return ProviderFailureAction.FAIL;
        }
        if (root instanceof TimeoutException
                || root instanceof ConnectException
                || root instanceof UnknownHostException
                || root instanceof IOException
                || root instanceof WebClientRequestException) {
            return ProviderFailureAction.RETRY_SAME;
        }
        String msg = root.getMessage() != null ? root.getMessage().toLowerCase() : "";
        if (msg.contains("timed out") || msg.contains("timeout") || msg.contains("connection")) {
            return ProviderFailureAction.RETRY_SAME;
        }
        return ProviderFailureAction.FAIL;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
