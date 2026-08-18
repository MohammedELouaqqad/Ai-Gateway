package com.backend.Ai_Gateway.support;

import com.backend.Ai_Gateway.config.TestJwtConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Spring Boot IT base.
 * <ul>
 *   <li>With Docker: ephemeral Postgres 16 (best practice for CI).</li>
 *   <li>Without Docker: falls back to {@code application.properties} local DB.</li>
 * </ul>
 * Redis guardrails stay off ({@code app.redis.enabled=false}) so ITs use in-memory stores.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtConfig.class)
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        PostgreSQLContainer<?> container = null;
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                        .withDatabaseName("Ai_Gateway_DB")
                        .withUsername("postgres")
                        .withPassword("postgres");
                container.start();
            }
        } catch (Throwable ignored) {
            container = null;
        }
        POSTGRES = container;
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("app.redis.enabled", () -> "false");
        if (POSTGRES != null && POSTGRES.isRunning()) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }
}
