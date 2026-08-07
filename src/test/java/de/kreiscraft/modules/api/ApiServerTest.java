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
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiServerTest {

    private static final String ALLOWED_ORIGIN = "https://kreiscraft.de";
    private static final String MALICIOUS_ORIGIN = "https://malicious.example";

    private ApiServer apiServer;
    private VerificationService verification;
    private MockScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        // Grab an ephemeral port then free it so Javalin can bind to it.
        int port;
        try (ServerSocket tmp = new ServerSocket(0)) {
            port = tmp.getLocalPort();
        }

        scheduler = new MockScheduler();

        ApiConfig config =
                new ApiConfig(
                        "127.0.0.1",
                        port,
                        List.of(ALLOWED_ORIGIN),
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
        verification =
                new VerificationService(scheduler, Duration.ofMinutes(2), Clock.systemUTC());
        LinkGuiPrompt linkPrompt = mock(LinkGuiPrompt.class);
        WhitelistService whitelist = mock(WhitelistService.class);
        ChatBridge chatBridge = new ChatBridge();

        apiServer =
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
    }

    @AfterEach
    void tearDown() {
        apiServer.stop();
        verification.shutdown();
    }

    // -------------------------------------------------------------------------
    // Test 1 — server starts and exposes the status endpoint
    // -------------------------------------------------------------------------

    @Test
    void start_bindsAllRoutes_andExposesStatusEndpoint() throws Exception {
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
    }

    // -------------------------------------------------------------------------
    // Test 2 — CORS: allowed origin receives Access-Control-Allow-Origin echo
    // -------------------------------------------------------------------------

    @Test
    void cors_allowedOrigin_receivesAcaoHeader() throws Exception {
        HttpResponse<String> resp =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + apiServer.port()
                                                        + "/status"))
                                        .header("Origin", ALLOWED_ORIGIN)
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode()).isEqualTo(200);
        Optional<String> acao = resp.headers().firstValue("Access-Control-Allow-Origin");
        assertThat(acao).as("ACAO header must be present for allowed origin").isPresent();
        assertThat(acao.get()).isEqualTo(ALLOWED_ORIGIN);
    }

    // -------------------------------------------------------------------------
    // Test 3 — CORS: disallowed origin must NOT receive Access-Control-Allow-Origin
    //           echoed back; Javalin sets BAD_REQUEST (400) for unrecognized origins.
    // -------------------------------------------------------------------------

    @Test
    void cors_disallowedOrigin_doesNotReceiveAcaoEcho() throws Exception {
        HttpResponse<String> resp =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + apiServer.port()
                                                        + "/status"))
                                        .header("Origin", MALICIOUS_ORIGIN)
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());

        // Javalin 6 sets 400 when the origin is not in the allow-list; the body
        // may still be present because the route handler still runs.
        // The critical security invariant: ACAO must NOT be set to the malicious origin.
        Optional<String> acao = resp.headers().firstValue("Access-Control-Allow-Origin");
        assertThat(acao)
                .as(
                        "ACAO header must NOT echo the malicious origin back "
                                + "(was: %s)",
                        acao.orElse("<absent>"))
                .satisfies(
                        opt -> assertThat(
                                opt.isEmpty()
                                        || !opt.get().equals(MALICIOUS_ORIGIN))
                                .isTrue());
    }
}
