package de.kreiscraft.modules.api.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.kreiscraft.modules.api.dto.PlayerData;
import de.kreiscraft.modules.api.metrics.ServerMetricsCollector;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerRoutesTest {

    @Test
    void get_players_returnsArray() {
        var metrics = mock(ServerMetricsCollector.class);
        when(metrics.currentPlayers())
                .thenReturn(List.of(new PlayerData("Alice", "u1", 20, 0, 0, 100)));
        var app = Javalin.create();
        PlayerRoutes.register(app, metrics);
        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/players");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).contains("Alice").startsWith("[");
        });
    }

    @Test
    void get_players_whenEmpty_returnsEmptyArray() {
        var metrics = mock(ServerMetricsCollector.class);
        when(metrics.currentPlayers()).thenReturn(List.of());
        var app = Javalin.create();
        PlayerRoutes.register(app, metrics);
        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/players");
            assertThat(response.body().string()).isEqualTo("[]");
        });
    }
}
