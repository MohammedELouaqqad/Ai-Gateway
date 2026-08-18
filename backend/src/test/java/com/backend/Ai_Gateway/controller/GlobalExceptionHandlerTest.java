package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.controller.dto.ApiErrorResponse;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.exceptions.ProviderNotImplementedException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GlobalExceptionHandler#handleNotImplemented} used to be exercised only by
 * {@code ChatProviderNotImplementedTest} (removed once every {@link ProviderType} got a real
 * adapter — see {@code ProviderCapabilitiesTest#everyProviderType_hasBothAdapters}, which
 * replaces that test's actual assertion). This closes the resulting gap: the handler itself
 * — constructing the exception directly, no real "unimplemented provider" needed to reach it.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotImplemented_returns501_withProviderTypeInMessage() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/chat");
        ProviderNotImplementedException ex = new ProviderNotImplementedException(ProviderType.SGLANG);

        ResponseEntity<ApiErrorResponse> response = handler.handleNotImplemented(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("SGLANG").contains("adaptateur manquant");
    }
}
