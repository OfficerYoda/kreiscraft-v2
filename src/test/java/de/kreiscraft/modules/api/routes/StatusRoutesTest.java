package de.kreiscraft.modules.api.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.kreiscraft.modules.api.dto.Metrics;
import de.kreiscraft.modules.api.metrics.ServerMetricsCollector;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

class StatusRoutesTest {

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
