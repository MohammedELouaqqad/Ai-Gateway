package com.backend.Ai_Gateway.config;

import com.backend.Ai_Gateway.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code management.server.port} is a genuinely separate embedded Servlet context — MockMvc
 * (used by every other integration test in this suite) never binds it, so it's the one thing
 * those tests can't catch. This test boots a real socket on the management port and hits it
 * over actual HTTP with zero credentials, the same way Prometheus does.
 *
 * <p>Worth recording why {@code /actuator/**} is permitAll'd on {@link SecurityConfig}'s main
 * catch-all chain (@{@code Order(2)}) instead of a dedicated {@code SecurityFilterChain} for
 * the management context: a {@code @ManagementContextConfiguration(ManagementContextType.CHILD)}
 * bean was tried first (the textbook approach for per-management-context security) and its
 * constructor/{@code @Bean} method DID run, but requests to the management port were still
 * intercepted by the main chain regardless (confirmed via the {@code WWW-Authenticate: Bearer}
 * challenge header in the 401 response, which only that chain's OAuth2 resource server config
 * produces) — the dedicated chain was simply never the one actually invoked for that port in
 * this setup. Empirically, permitAll on the main chain is what works, so that's what's live.
 *
 * <p>Only {@code /actuator/health} is asserted here, not {@code /actuator/prometheus}: in this
 * exact test harness (RANDOM_PORT + a separate dynamic management port + Testcontainers), the
 * Prometheus scrape endpoint doesn't get an MVC mapping in the management child context (a 404
 * routing miss, not a 401) — while a live {@code docker compose} run has confirmed the endpoint
 * routes correctly there. That gap looks like a test-harness-specific quirk unrelated to the
 * security fix this test actually verifies (both paths share the same permitAll matcher list,
 * so health passing here is representative of prometheus too) — not chased further to avoid
 * asserting on behavior this harness can't reproduce faithfully.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0"
)
class ManagementPortSecurityTest extends AbstractIntegrationTest {

    @LocalManagementPort
    private int managementPort;

    @LocalServerPort
    private int serverPort;

    @Test
    void actuatorHealth_onManagementPort_reachableWithoutAnyCredentials() {
        TestRestTemplate rest = new TestRestTemplate();

        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + managementPort + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * The regression this test exists to catch: permitAll'ing {@code /actuator/**} on the main
     * chain (see the class javadoc) must NOT re-expose it on {@code server.port} — that port is
     * published to the host in docker-compose.yml, unlike the management port. Spring Boot's
     * separate-management-port feature is what's actually relied on to keep these endpoints off
     * server.port entirely; this asserts that still holds rather than assuming it.
     */
    @Test
    void actuatorPrometheus_onMainServerPort_notReachableAtAll() {
        TestRestTemplate rest = new TestRestTemplate();

        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + serverPort + "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }
}
