package de.kreiscraft.modules.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kreiscraft.modules.api.metrics.ServerMetricsCollector;
import de.kreiscraft.modules.api.verification.LinkGuiPrompt;
import de.kreiscraft.modules.api.verification.VerificationService;
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.whitelist.WhitelistService;
import de.kreiscraft.testsupport.MockScheduler;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;

class ApiServerTest {

    @Test
    void start_bindsAllRoutes_andExposesStatusEndpoint() throws Exception {
        // Grab an ephemeral port then free it so Javalin can bind to it.
        int port;
        try (ServerSocket tmp = new ServerSocket(0)) {
            port = tmp.getLocalPort();
        }

        MockScheduler scheduler = new MockScheduler();

        // Minimal ApiConfig pointing at the chosen ephemeral port.
        ApiConfig config =
                new ApiConfig(
                        "127.0.0.1",
                        port,
                        List.of("https://kreiscraft.de"),
                        new ApiConfig.SessionCookie("sid", Duration.ofDays(30)),
                        Duration.ofSeconds(20),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(1),
                        Duration.ofMinutes(2),
                        "");

        Server mockBukkitServer = mock(Server.class);
        when(mockBukkitServer.getTPS()).thenReturn(new double[] {20.0, 20.0, 20.0});
        when(mockBukkitServer.getOnlinePlayers()).thenReturn(List.of());

        ServerMetricsCollector metrics =
                new ServerMetricsCollector(mockBukkitServer, scheduler, Duration.ofSeconds(1));
        VerificationService verification =
                new VerificationService(scheduler, Duration.ofMinutes(2), Clock.systemUTC());
        LinkGuiPrompt linkPrompt = mock(LinkGuiPrompt.class);
        WhitelistService whitelist = mock(WhitelistService.class);
        ChatBridge chatBridge = new ChatBridge();

        ApiServer apiServer =
                new ApiServer(
                        config,
                        metrics,
                        verification,
                        linkPrompt,
                        whitelist,
                        chatBridge,
                        mock(de.kreiscraft.core.message.MessageService.class),
                        scheduler,
                        mockBukkitServer,
                        new ObjectMapper(),
                        Logger.getLogger("test"));

        apiServer.start();
        try {
            HttpResponse<String> resp =
                    HttpClient.newHttpClient()
                            .send(
                                    HttpRequest.newBuilder(
                                            URI.create(
                                                    "http://127.0.0.1:"
                                                            + apiServer.port()
                                                            + "/status"))
                                            .GET()
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString());
            assertThat(resp.statusCode()).isEqualTo(200);
        } finally {
            apiServer.stop();
            verification.shutdown();
        }
    }
}
