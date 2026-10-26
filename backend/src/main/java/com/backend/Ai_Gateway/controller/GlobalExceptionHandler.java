package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.controller.dto.ApiErrorResponse;
import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException;
import com.backend.Ai_Gateway.exceptions.ProviderNotImplementedException;
import com.backend.Ai_Gateway.exceptions.RessourceAlreadyExistsException;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RessourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(RessourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(RessourceAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(RessourceAlreadyExistsException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Accès refusé", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Requête invalide");
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, "Corps de requête invalide", request);
    }

    @ExceptionHandler(GuardrailsExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleGuardrails(
            GuardrailsExceededException ex,
            HttpServletRequest request
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                ex.getReason().metricLabel(),
                ex.getResetsAt()
        );
        // Force JSON even when client Accept is only text/event-stream (chat SSE)
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        Instant.now(),
                        HttpStatus.NOT_ACCEPTABLE.value(),
                        HttpStatus.NOT_ACCEPTABLE.getReasonPhrase(),
                        "Type de réponse non acceptable",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(ProviderNotImplementedException.class)
    public ResponseEntity<ApiErrorResponse> handleNotImplemented(
            ProviderNotImplementedException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.NOT_IMPLEMENTED, ex.getMessage(), request);
    }

    /**
     * Le fournisseur LLM (Ollama/OpenAI/...) a répondu avec un statut d'erreur — ex. 404
     * "model not found" pour un modèle mal configuré côté app. Sans ce handler, ça tombait
     * dans {@link #handleGeneric} : "Une erreur interne est survenue", message du provider
     * perdu (pas même loggé). 502 : c'est le fournisseur en amont qui a échoué, pas ce service.
     */
    @ExceptionHandler({RestClientResponseException.class, WebClientResponseException.class})
    public ResponseEntity<ApiErrorResponse> handleProviderHttpError(Exception ex, HttpServletRequest request) {
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

        String detail = body != null && !body.isBlank() ? " : " + truncate(body, 300) : "";
        return build(
                HttpStatus.BAD_GATEWAY,
                "Le fournisseur IA a retourné une erreur (HTTP " + status + ")" + detail,
                request
        );
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Thrown by Spring's multipart parser itself (see {@code spring.servlet.multipart.max-*}
     * in application.properties) BEFORE the request ever reaches {@code FileController} /
     * {@code AttachmentService#upload} — its own {@code app.uploads.max-size-bytes} check never
     * runs for a file this large, so it must be handled here instead of falling to
     * {@link #handleGeneric} as a 500.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "file: exceeds max upload size", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        // Avant ce fix : exception avalée sans trace, "Une erreur interne est survenue" était
        // tout ce qui restait, y compris dans les logs — impossible à débugger après coup.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue", request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
