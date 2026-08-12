package com.backend.Ai_Gateway.provider;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.provider.dto.ChatChunk;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Exercises {@link OpenAiCompatibleProvider#chat}/{@link OpenAiCompatibleProvider#stream}
 * against real (mocked/local) HTTP once, for the whole family — closes a gap that predates
 * this class: nothing previously asserted on the actual request sent over the wire, only on
 * {@code types()}. Per-subclass tests stay thin (types/defaultBaseUrl/resolveApiKey only).
 */
class OpenAiCompatibleProviderTest {

    private static final String SAMPLE_CHAT_RESPONSE = """
            {"choices":[{"message":{"role":"assistant","content":"hi"}}],
             "usage":{"prompt_tokens":3,"completion_tokens":1}}
            """;

    private final ChatRequest sampleRequest =
            new ChatRequest("gpt-4o-mini", List.of(new ChatMessage(MessageRole.USER, "hello")));

    @Test
    void chat_openAiProvider_sendsBearerAuthHeader() {
        RestClient.Builder restBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restBuilder).build();
        OpenAIProvider provider = new OpenAIProvider(restBuilder, WebClient.builder(), new ObjectMapper(), null);

        Provider providerRow = new Provider();
        providerRow.setApiKey("sk-test-key");
        providerRow.setBaseUrl("https://api.openai.com/v1");

        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer sk-test-key"))
                .andRespond(withSuccess(SAMPLE_CHAT_RESPONSE, MediaType.APPLICATION_JSON));

        ChatResponse response = provider.chat(providerRow, sampleRequest);

        assertThat(response.content()).isEqualTo("hi");
        server.verify();
    }

    @Test
    void chat_noAuthHeaderWhenKeyBlank_forOptionalAuthFamilyMember() {
        RestClient.Builder restBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restBuilder).build();
        VllmProvider provider = new VllmProvider(restBuilder, WebClient.builder(), new ObjectMapper());

        Provider providerRow = new Provider(); // no apiKey set — local server, no auth required
        providerRow.setBaseUrl("http://localhost:9999"); // "/v1" auto-appended — see baseUrlVersionSuffix()

        server.expect(requestTo("http://localhost:9999/v1/chat/completions"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(SAMPLE_CHAT_RESPONSE, MediaType.APPLICATION_JSON));

        provider.chat(providerRow, sampleRequest);

        server.verify();
    }

    @Test
    void chat_vllmProvider_sendsBearerAuthWhenKeyConfigured() {
        RestClient.Builder restBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restBuilder).build();
        VllmProvider provider = new VllmProvider(restBuilder, WebClient.builder(), new ObjectMapper());

        Provider providerRow = new Provider();
        providerRow.setApiKey("vllm-secret");
        providerRow.setBaseUrl("http://localhost:8000/v1");

        server.expect(requestTo("http://localhost:8000/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer vllm-secret"))
                .andRespond(withSuccess(SAMPLE_CHAT_RESPONSE, MediaType.APPLICATION_JSON));

        provider.chat(providerRow, sampleRequest);

        server.verify();
    }

    @Test
    void chat_customBaseUrlAlreadyEndingInV1_isNotDoubled() {
        RestClient.Builder restBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restBuilder).build();
        VllmProvider provider = new VllmProvider(restBuilder, WebClient.builder(), new ObjectMapper());

        Provider providerRow = new Provider();
        providerRow.setBaseUrl("http://localhost:9999/v1"); // already has it — must not become /v1/v1

        server.expect(requestTo("http://localhost:9999/v1/chat/completions"))
                .andRespond(withSuccess(SAMPLE_CHAT_RESPONSE, MediaType.APPLICATION_JSON));

        provider.chat(providerRow, sampleRequest);

        server.verify();
    }

    @Test
    void chat_azureCustomBaseUrl_neverGetsV1Appended() {
        RestClient.Builder restBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restBuilder).build();
        AzureOpenAIProvider provider = new AzureOpenAIProvider(restBuilder, WebClient.builder(), new ObjectMapper());

        Provider providerRow = new Provider();
        providerRow.setApiKey("azure-secret");
        providerRow.setBaseUrl("https://my-resource.openai.azure.com"); // Azure never uses /v1

        server.expect(requestTo(
                        "https://my-resource.openai.azure.com/openai/deployments/gpt-4o-mini/chat/completions?api-version=2024-06-01"))
                .andRespond(withSuccess(SAMPLE_CHAT_RESPONSE, MediaType.APPLICATION_JSON));

        provider.chat(providerRow, sampleRequest);

        server.verify();
    }

    @Test
    void chat_azureProvider_hitsDeploymentScopedPathWithApiVersion() {
        RestClient.Builder restBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restBuilder).build();
        AzureOpenAIProvider provider = new AzureOpenAIProvider(restBuilder, WebClient.builder(), new ObjectMapper());

        Provider providerRow = new Provider();
        providerRow.setApiKey("azure-secret");
        providerRow.setBaseUrl("https://my-resource.openai.azure.com");

        server.expect(requestTo(
                        "https://my-resource.openai.azure.com/openai/deployments/gpt-4o-mini/chat/completions?api-version=2024-06-01"))
                .andExpect(header("api-key", "azure-secret"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(SAMPLE_CHAT_RESPONSE, MediaType.APPLICATION_JSON));

        ChatResponse response = provider.chat(providerRow, sampleRequest);

        assertThat(response.content()).isEqualTo("hi");
        server.verify();
    }

    @Test
    void stream_azureProvider_sendsApiKeyHeader_overRealLocalServer() throws IOException {
        AtomicReference<String> capturedApiKey = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        HttpServer httpServer = startSseServer(capturedApiKey, capturedAuth);
        try {
            AzureOpenAIProvider provider =
                    new AzureOpenAIProvider(RestClient.builder(), WebClient.builder(), new ObjectMapper());
            Provider providerRow = new Provider();
            providerRow.setApiKey("azure-secret");
            providerRow.setBaseUrl("http://localhost:" + httpServer.getAddress().getPort());

            List<ChatChunk> chunks = provider.stream(providerRow, sampleRequest)
                    .collectList()
                    .block(Duration.ofSeconds(5));

            assertThat(chunks).isNotNull().isNotEmpty();
            assertThat(chunks.get(0).content()).isEqualTo("hi");
            assertThat(capturedApiKey.get()).isEqualTo("azure-secret");
            assertThat(capturedAuth.get()).isNull();
        } finally {
            httpServer.stop(0);
        }
    }

    private static HttpServer startSseServer(
            AtomicReference<String> capturedApiKey,
            AtomicReference<String> capturedAuth
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        // Azure's deployment-scoped path (see AzureOpenAIProvider#chatPath) — sampleRequest's
        // model is "gpt-4o-mini", which becomes the deployment name in the URL.
        server.createContext("/openai/deployments/gpt-4o-mini/chat/completions", exchange -> {
            capturedApiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String body = "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\n"
                    + "data: [DONE]\n\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return server;
    }
}
