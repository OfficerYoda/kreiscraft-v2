package de.kreiscraft.modules.api.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import de.kreiscraft.modules.api.dto.Metrics;
import de.kreiscraft.modules.api.metrics.ServerMetricsCollector;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

class StatusRoutesTest {

    @Test
    void get_ping_returnsUncached204WithoutReadingMetrics() {
        var metrics = mock(ServerMetricsCollector.class);
        var app = Javalin.create(cfg -> cfg.bundledPlugins.enableCors(
                cors -> cors.addRule(rule -> {
                    rule.allowCredentials = true;
                    rule.allowHost("http://localhost:3000");
                })));
        StatusRoutes.register(app, metrics);
        JavalinTest.test(app, (server, client) -> {
            for (int i = 0; i < 2; i++) {
                try (var response = client.get("/ping", req -> req.header("Origin", "http://localhost:3000"))) {
                    assertThat(response.code()).isEqualTo(204);
                    assertThat(response.body().string()).isEmpty();
                    assertThat(response.header("Cache-Control")).isEqualTo("no-store");
                    assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");
                }
            }
            try (var response = client.get("/ping", req -> req.header("Origin", "https://disallowed.example"))) {
                assertThat(response.code()).isEqualTo(400);
                assertThat(response.header("Access-Control-Allow-Origin")).isNull();
            }
        });
        verifyNoInteractions(metrics);
    }

    @Test
    void get_status_returns200WithJsonBody() {
        var metrics = mock(ServerMetricsCollector.class);
        when(metrics.currentMetrics()).thenReturn(new Metrics("Online", 20.0, 1, 100, 1000, 5.0));

        var app = Javalin.create();
        StatusRoutes.register(app, metrics);

        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/status");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).contains("\"status\":\"Online\"").contains("\"tps\":20.0");
        });
    }
}
