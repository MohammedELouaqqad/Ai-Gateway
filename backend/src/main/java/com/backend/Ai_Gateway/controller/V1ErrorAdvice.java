package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException;
import com.backend.Ai_Gateway.exceptions.ProviderNotImplementedException;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * OpenAI-shaped errors ({@code {"error":{message,type,param,code}}}) for {@code /v1/**} only
 * — {@link GlobalExceptionHandler} keeps serving {@code /api/**} with {@code ApiErrorResponse}
 * unchanged; scoping via {@code assignableTypes} avoids touching that contract at all.
 * <p>
 * {@code @Order(HIGHEST_PRECEDENCE)}: {@code assignableTypes} only decides whether this
 * advice is <em>eligible</em> for a given controller — an unscoped advice (like
 * {@link GlobalExceptionHandler}) is eligible for every controller too. Without an explicit
 * order, Spring picks whichever eligible advice comes first by bean name, not by
 * specificity, and the wrong (non-OpenAI-shaped) error body wins.
 */
@RestControllerAdvice(assignableTypes = {V1ChatController.class, V1ModelsController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class V1ErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(V1ErrorAdvice.class);

    private record ErrorBody(OpenAiError error) {}

    private record OpenAiError(String message, String type, String param, String code) {}

    @ExceptionHandler(RessourceNotFoundException.class)
    public ResponseEntity<ErrorBody> handleNotFound(RessourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), "invalid_request_error", "model_not_found");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleBadRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "invalid_request_error", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return build(HttpStatus.BAD_REQUEST, message, "invalid_request_error", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Invalid request body", "invalid_request_error", null);
    }

    @ExceptionHandler(GuardrailsExceededException.class)
    public ResponseEntity<ErrorBody> handleGuardrails(GuardrailsExceededException ex) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), "rate_limit_error", ex.getReason().metricLabel());
    }

    @ExceptionHandler(ProviderNotImplementedException.class)
    public ResponseEntity<ErrorBody> handleNotImplemented(ProviderNotImplementedException ex) {
        return build(HttpStatus.NOT_IMPLEMENTED, ex.getMessage(), "invalid_request_error", "provider_not_implemented");
    }

    @ExceptionHandler({RestClientResponseException.class, WebClientResponseException.class})
    public ResponseEntity<ErrorBody> handleProviderHttpError(Exception ex) {
        int status;
        String body;
        if (ex instanceof RestClientResponseException e) {
            status = e.getStatusCode().value();
            body = e.getResponseBodyAsString();
        } else {
            WebClientResponseException e = (WebClientResponseException) ex;
            status = e.getStatusCode().value();
            body = e.getResponseBodyAsString();
        }
        log.warn("Provider HTTP error: status={} body={}", status, body, ex);
        String message = "Upstream provider error (HTTP " + status + ")"
                + (body != null && !body.isBlank() ? ": " + truncate(body, 300) : "");
        return build(HttpStatus.BAD_GATEWAY, message, "api_error", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleGeneric(Exception ex) {
        log.error("Unhandled exception in /v1/**", ex);
        return build(HttpStatus.BAD_GATEWAY, "Upstream provider error", "api_error", null);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static ResponseEntity<ErrorBody> build(HttpStatus status, String message, String type, String code) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorBody(new OpenAiError(message, type, null, code)));
    }
}
