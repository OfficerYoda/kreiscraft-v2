package de.kreiscraft.modules.api;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.io.StringReader;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class ApiConfigTest {

    @Test
    void parsesFullConfigBlock() throws Exception {
        String yaml =
                """
                        enabled: true
                        host: "127.0.0.1"
                        port: 9090
                        cors-origins: ["https://kreiscraft.de", "http://localhost:5173"]
                        session-cookie:
                          name: "sid"
                          max-age: 7d
                        heartbeat-interval: 30s
                        async-timeout: 5s
                        metrics-refresh-interval: 2s
                        verification-timeout: 3m
                        admin-api-key: "secret"
                        """;
        var loader = YamlConfigurationLoader.builder()
                .source(() -> new java.io.BufferedReader(new StringReader(yaml)))
                .defaultOptions(o -> o.serializers(KreiscraftSerializers.builder().build()))
                .build();
        var node = loader.load();
        ApiConfig cfg = node.get(ApiConfig.class);
        assertThat(cfg.host()).isEqualTo("127.0.0.1");
        assertThat(cfg.port()).isEqualTo(9090);
        assertThat(cfg.corsOrigins()).containsExactly("https://kreiscraft.de", "http://localhost:5173");
        assertThat(cfg.sessionCookie().name()).isEqualTo("sid");
        assertThat(cfg.sessionCookie().maxAge()).isEqualTo(Duration.ofDays(7));
        assertThat(cfg.heartbeatInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(cfg.adminApiKey()).isEqualTo("secret");
    }

    @Test
    void emptyConfigYieldsAllDefaults() throws Exception {
        var loader = YamlConfigurationLoader.builder()
                .source(() -> new java.io.BufferedReader(new StringReader("enabled: true\n")))
                .defaultOptions(o -> o.serializers(KreiscraftSerializers.builder().build()))
                .build();
        ApiConfig cfg = loader.load().get(ApiConfig.class);
        assertThat(cfg.host()).isEqualTo("0.0.0.0");
        assertThat(cfg.port()).isEqualTo(8080);
        assertThat(cfg.corsOrigins()).containsExactly("https://kreiscraft.de");
        assertThat(cfg.sessionCookie().name()).isEqualTo("website_user_uuid");
        assertThat(cfg.sessionCookie().maxAge()).isEqualTo(Duration.ofDays(30));
        assertThat(cfg.heartbeatInterval()).isEqualTo(Duration.ofSeconds(20));
        assertThat(cfg.adminApiKey()).isEmpty();
    }
}
