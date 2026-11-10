package de.kreiscraft.modules.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.command.CommandRegistrar;
import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.modules.api.verification.VerificationService;
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.shared.gui.GuiRegistry;
import de.kreiscraft.shared.whitelist.WhitelistService;
import de.kreiscraft.testsupport.MockScheduler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class ApiModuleTest {
    @TempDir
    Path dataDirectory;

    private MockScheduler scheduler;
    private ServiceRegistry services;
    private JavaPlugin plugin;
    private Server server;
    private ApiModule module;
    private int testPort;
    private ConfigurationNode defaultConfig;

    @BeforeEach
    void setUp() throws Exception {
        scheduler = new MockScheduler();
        services = new ServiceRegistry();

        // Publish required shared services.
        services.publish(ChatBridge.class, new ChatBridge());
        services.publish(WhitelistService.class, mock(WhitelistService.class));
        services.publish(Gui.class, new Gui(new GuiRegistry()));

        // Mock Bukkit dependencies.
        plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getDataFolder()).thenReturn(dataDirectory.toFile());
        when(server.getTPS()).thenReturn(new double[] {20.0, 20.0, 20.0});
        when(server.getOnlinePlayers()).thenReturn(List.of());

        // Find a free port for this test.
        try (ServerSocket tmp = new ServerSocket(0)) {
            testPort = tmp.getLocalPort();
        }

        defaultConfig = buildConfig(testPort, "https://kreiscraft.de");
        module = new ApiModule();
    }

    @AfterEach
    void tearDown() {
        module.disable();
    }

    @Test
    void enable_publishesVerificationService() {
        module.enable(makeContext(defaultConfig));

        assertThat(services.get(VerificationService.class)).isNotNull();
    }

    @Test
    void enable_startsApiServer() throws Exception {
        module.enable(makeContext(defaultConfig));

        HttpResponse<String> resp =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + testPort
                                                        + "/status"))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
    }

    @Test
    void disable_stopsApiServer_andShutsDownVerification() throws Exception {
        module.enable(makeContext(defaultConfig));

        // Verify the server is up before disabling.
        HttpResponse<String> before =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + testPort
                                                        + "/status"))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        assertThat(before.statusCode()).isEqualTo(200);

        module.disable();

        // After stop, connecting to the port must fail.
        boolean connectionRefused = false;
        try {
            HttpClient.newHttpClient()
                    .send(
                            HttpRequest.newBuilder(
                                    URI.create(
                                            "http://127.0.0.1:" + testPort + "/status"))
                                    .GET()
                                    .timeout(Duration.ofSeconds(2))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            connectionRefused = true;
        }
        assertThat(connectionRefused).as("server should no longer accept connections").isTrue();
    }

    @Test
    void onConfigReload_updatesConfigReference() throws Exception {
        module.enable(makeContext(defaultConfig));

        // Same host + port, different cors-origins → triggers restart in onConfigReload.
        ConfigurationNode newCfgNode = buildConfig(testPort, "https://other.example.com");
        module.onConfigReload(newCfgNode);

        // Server was restarted on the same port; must still respond.
        HttpResponse<String> resp =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + testPort
                                                        + "/status"))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
    }

    // ---- helpers ----

    private ModuleContext makeContext(ConfigurationNode cfg) {
        return new ModuleContext(
                plugin,
                cfg,
                mock(MessageService.class),
                services,
                scheduler,
                mock(CommandRegistrar.class),
                mock(EventBus.class));
    }

    private static ConfigurationNode buildConfig(int port, String corsOrigin) throws Exception {
        String yaml =
                "host: \"127.0.0.1\"\n"
                        + "port: "
                        + port
                        + "\n"
                        + "cors-origins:\n"
                        + "  - \""
                        + corsOrigin
                        + "\"\n"
                        + "heartbeat-interval: \"20s\"\n"
                        + "async-timeout: \"10s\"\n"
                        + "metrics-refresh-interval: \"1s\"\n"
                        + "verification-timeout: \"2m\"\n"
                        + "admin-api-key: \"\"\n"
                        + "session-cookie:\n"
                        + "  name: \"sid\"\n"
                        + "  max-age: \"30d\"\n";
        return YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(yaml)))
                .defaultOptions(o -> o.serializers(KreiscraftSerializers.builder().build()))
                .build()
                .load();
    }
}
