package de.kreiscraft.modules.api.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.api.ApiConfig;
import de.kreiscraft.modules.api.dto.PlayerData;
import de.kreiscraft.modules.api.metrics.ServerMetricsCollector;
import de.kreiscraft.modules.api.verification.LinkGuiPrompt;
import de.kreiscraft.modules.api.verification.VerificationService;
import de.kreiscraft.modules.api.verification.VerificationStatus;
import de.kreiscraft.testsupport.MockScheduler;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkRoutesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiConfig config;
    private MockScheduler scheduler;
    private VerificationService verification;
    private LinkGuiPrompt linkPrompt;
    private ServerMetricsCollector metrics;
    private Server server;
    private MessageService messages;
    private Javalin app;
    private String cookieName;
    private UUID sessionUuid;

    @BeforeEach
    void setUp() {
        config = new ApiConfig(null, 0, null, null, null, null, null, null, null);
        scheduler = new MockScheduler();
        verification = mock(VerificationService.class);
        linkPrompt = mock(LinkGuiPrompt.class);
        metrics = mock(ServerMetricsCollector.class);
        server = mock(Server.class);
        messages = mock(MessageService.class);
        cookieName = config.sessionCookie().name(); // "website_user_uuid"
        sessionUuid = UUID.randomUUID();

        app = Javalin.create();
        LinkRoutes.register(
                app, config, verification, linkPrompt, metrics, scheduler, server, messages);
    }

    // -----------------------------------------------------------------------
    // POST /link-player
    // -----------------------------------------------------------------------

    @Test
    void postLink_missingCookie_401() {
        JavalinTest.test(
                app,
                (srv, client) -> {
                    var response = client.post("/link-player", "{\"username\":\"SomePlayer\"}");
                    assertThat(response.code()).isEqualTo(401);
                    var body = MAPPER.readTree(response.body().string());
                    assertThat(body.get("error").asText()).isEqualTo("missing_session");
                });
    }

    @Test
    void postLink_playerOffline_422() {
        when(server.getPlayerExact("OfflinePlayer")).thenReturn(null);

        JavalinTest.test(
                app,
                (srv, client) -> {
                    var response =
                            client.post(
                                    "/link-player",
                                    "{\"username\":\"OfflinePlayer\"}",
                                    req -> req.addHeader(
                                            "Cookie",
                                            cookieName + "=" + sessionUuid));
                    assertThat(response.code()).isEqualTo(422);
                    var body = MAPPER.readTree(response.body().string());
                    assertThat(body.get("error").asText()).isEqualTo("player_offline");
                });
    }

    @Test
    void postLink_playerOnline_202_verificationSent_andPromptDispatched() {
        Player player = mock(Player.class);
        UUID playerUuid = UUID.randomUUID();
        when(player.getName()).thenReturn("OnlinePlayer");
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(server.getPlayerExact("OnlinePlayer")).thenReturn(player);

        // Stub sendVerification to invoke the dispatchPromptOnMain runnable (arg 3).
        doAnswer(
                inv -> {
                    Runnable dispatchPromptOnMain = inv.getArgument(3);
                    dispatchPromptOnMain.run();
                    return VerificationService.SendResult.SENT;
                })
                .when(verification)
                .sendVerification(any(), any(), any(), any());

        JavalinTest.test(
                app,
                (srv, client) -> {
                    var response =
                            client.post(
                                    "/link-player",
                                    "{\"username\":\"OnlinePlayer\"}",
                                    req -> req.addHeader(
                                            "Cookie",
                                            cookieName + "=" + sessionUuid));
                    assertThat(response.code()).isEqualTo(202);
                    var body = MAPPER.readTree(response.body().string());
                    assertThat(body.get("status").asText()).isEqualTo("verification_sent");
                });

        verify(linkPrompt)
                .open(
                        eq(player),
                        eq("browser " + sessionUuid.toString().substring(0, 8)),
                        any(),
                        any());
    }

    @Test
    void postLink_alreadyPending_409() {
        Player player = mock(Player.class);
        UUID playerUuid = UUID.randomUUID();
        when(player.getName()).thenReturn("OnlinePlayer");
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(server.getPlayerExact("OnlinePlayer")).thenReturn(player);
        when(verification.sendVerification(any(), any(), any(), any()))
                .thenReturn(VerificationService.SendResult.ALREADY_PENDING);

        JavalinTest.test(
                app,
                (srv, client) -> {
                    var response =
                            client.post(
                                    "/link-player",
                                    "{\"username\":\"OnlinePlayer\"}",
                                    req -> req.addHeader(
                                            "Cookie",
                                            cookieName + "=" + sessionUuid));
                    assertThat(response.code()).isEqualTo(409);
                    var body = MAPPER.readTree(response.body().string());
                    assertThat(body.get("status").asText()).isEqualTo("already_pending");
                });
    }

    // -----------------------------------------------------------------------
    // GET /linked-player
    // -----------------------------------------------------------------------

    @Test
    void getLinked_missingCookie_401() {
        JavalinTest.test(
                app,
                (srv, client) -> {
                    var response = client.get("/linked-player");
                    assertThat(response.code()).isEqualTo(401);
                    var body = MAPPER.readTree(response.body().string());
                    assertThat(body.get("error").asText()).isEqualTo("missing_session");
                });
    }

    @Test
    void getLinked_noEntry_returnsNotLinked() {
        when(verification.getStatus(sessionUuid)).thenReturn(null);

        JavalinTest.test(
                app,
                (srv, client) -> {
                    var response =
                            client.get(
                                    "/linked-player",
                                    req -> req.addHeader(
                                            "Cookie",
                                            cookieName + "=" + sessionUuid));
                    assertThat(response.code()).isEqualTo(200);
                    var body = MAPPER.readTree(response.body().string());
                    assertThat(body.get("status").asText()).isEqualTo("not_linked");
                });
    }

    @Test
    void getLinked_pending_returnsPending() {
        when(verification.getStatus(sessionUuid)).thenReturn(VerificationStatus.PENDING);

        JavalinTest.test(
                app,
                (srv, client) -> {
                    var response =
                            client.get(
                                    "/linked-player",
                                    req -> req.addHeader(
                                            "Cookie",
                                            cookieName + "=" + sessionUuid));
                    assertThat(response.code()).isEqualTo(200);
                    var body = MAPPER.readTree(response.body().string());
                    assertThat(body.get("status").asText()).isEqualTo("pending");
                });
    }

    @Test
    void getLinked_accepted_online_returnsAcceptedWithPlayerData() {
        UUID playerUuid = UUID.randomUUID();
        PlayerData data =
                new PlayerData("Player1", playerUuid.toString(), 20, 5, 3, 10000L);
        when(verification.getStatus(sessionUuid)).thenReturn(VerificationStatus.ACCEPTED);
        when(verification.getLinkedPlayer(sessionUuid)).thenReturn(playerUuid);
        when(metrics.playerByUuid(playerUuid)).thenReturn(data);

        JavalinTest.test(
                app,
                (srv, client) -> {
                    var response =
                            client.get(
                                    "/linked-player",
                                    req -> req.addHeader(
                                            "Cookie",
                                            cookieName + "=" + sessionUuid));
                    assertThat(response.code()).isEqualTo(200);
                    var body = MAPPER.readTree(response.body().string());
                    assertThat(body.get("status").asText()).isEqualTo("accepted");
                    assertThat(body.get("playerData").get("name").asText()).isEqualTo("Player1");
                });
    }

    @Test
    void getLinked_accepted_offline_returnsAcceptedWithoutPlayerData() {
        UUID playerUuid = UUID.randomUUID();
        when(verification.getStatus(sessionUuid)).thenReturn(VerificationStatus.ACCEPTED);
        when(verification.getLinkedPlayer(sessionUuid)).thenReturn(playerUuid);
        when(metrics.playerByUuid(playerUuid)).thenReturn(null);

        JavalinTest.test(
                app,
                (srv, client) -> {
                    var response =
                            client.get(
                                    "/linked-player",
                                    req -> req.addHeader(
                                            "Cookie",
                                            cookieName + "=" + sessionUuid));
                    assertThat(response.code()).isEqualTo(200);
                    var body = MAPPER.readTree(response.body().string());
                    assertThat(body.get("status").asText()).isEqualTo("accepted");
                    assertThat(body.has("playerData")).isFalse();
                });
    }

    // -----------------------------------------------------------------------
    // POST /unlink-player
    // -----------------------------------------------------------------------

    @Test
    void postUnlink_missingCookie_401() {
        JavalinTest.test(
                app,
                (srv, client) -> {
                    var response = client.post("/unlink-player", "");
                    assertThat(response.code()).isEqualTo(401);
                    var body = MAPPER.readTree(response.body().string());
                    assertThat(body.get("error").asText()).isEqualTo("missing_session");
                });
    }

    @Test
    void postUnlink_notLinked_400() {
        when(verification.getLinkedPlayer(sessionUuid)).thenReturn(null);

        JavalinTest.test(
                app,
                (srv, client) -> {
                    var response =
                            client.post(
                                    "/unlink-player",
                                    "",
                                    req -> req.addHeader(
                                            "Cookie",
                                            cookieName + "=" + sessionUuid));
                    assertThat(response.code()).isEqualTo(400);
                    var body = MAPPER.readTree(response.body().string());
                    assertThat(body.get("error").asText()).isEqualTo("not_linked");
                });
    }

    @Test
    void postUnlink_linked_200_andSchedulesNotifyOnMain() {
        UUID linkedPlayerUuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(verification.getLinkedPlayer(sessionUuid)).thenReturn(linkedPlayerUuid);
        when(server.getPlayer(linkedPlayerUuid)).thenReturn(player);

        JavalinTest.test(
                app,
                (srv, client) -> {
                    var response =
                            client.post(
                                    "/unlink-player",
                                    "",
                                    req -> req.addHeader(
                                            "Cookie",
                                            cookieName + "=" + sessionUuid));
                    assertThat(response.code()).isEqualTo(200);
                    var body = MAPPER.readTree(response.body().string());
                    assertThat(body.get("status").asText()).isEqualTo("unlinked");
                });

        verify(verification).unlink(sessionUuid);
        verify(messages).send(player, "api.unlinked");
    }
}
