package com.backend.Ai_Gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/** OpenAI-shaped 401 for {@code /v1/**} so existing OpenAI-compatible clients parse it as usual. */
public class ApiKeyAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private record ErrorBody(OpenAiError error) {}

    private record OpenAiError(String message, String type, String param, String code) {}

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorBody body = new ErrorBody(new OpenAiError(
                "Incorrect API key provided, or the key has been revoked.",
                "invalid_request_error",
                null,
                "invalid_api_key"
        ));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
