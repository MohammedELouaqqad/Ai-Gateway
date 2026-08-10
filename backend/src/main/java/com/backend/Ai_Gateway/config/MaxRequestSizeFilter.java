package com.backend.Ai_Gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Hard cap on {@code /v1/**} request bodies, rejected before Spring/Jackson reads a single
 * byte into memory — {@code @Size} on {@code V1ChatCompletionRequest}/{@code V1ChatMessage}
 * only rejects AFTER the whole body is already deserialized, which doesn't stop the
 * deserialization-time memory spike itself for an oversized {@code messages[]}/{@code content}.
 *
 * <p>Relies on the client-sent {@code Content-Length} header, which every real HTTP client
 * (browsers, the OpenAI SDKs, curl) sends for a JSON body — a request smuggled via chunked
 * transfer-encoding with no {@code Content-Length} would bypass this specific check, but
 * {@code @Size} still applies once such a body is fully buffered downstream. The fully robust
 * backstop against a chunked-encoding bypass is a body-size cap at the reverse-proxy layer
 * (e.g. nginx {@code client_max_body_size}) in front of the gateway — not applicable today
 * since this stack has no reverse proxy in front of it, but worth adding there first if one
 * is introduced later.
 */
public class MaxRequestSizeFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public MaxRequestSizeFilter(long maxBytes, ObjectMapper objectMapper) {
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    private record ErrorBody(OpenAiError error) {}

    private record OpenAiError(String message, String type, String param, String code) {}

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorBody body = new ErrorBody(new OpenAiError(
                    "Request body exceeds the " + maxBytes + " byte limit.",
                    "invalid_request_error",
                    null,
                    "request_too_large"
            ));
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
