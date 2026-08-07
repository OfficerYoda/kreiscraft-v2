package de.kreiscraft.modules.api.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.api.ApiConfig;
import de.kreiscraft.shared.whitelist.WhitelistService;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WhitelistRoutesTest {

    /** A synchronous scheduler fake that runs the runnable immediately on the calling thread. */
    private static final Scheduler SYNC_SCHEDULER =
            new Scheduler() {
                @Override
                public Task runOnMain(Runnable task) {
                    task.run();
                    return new Task() {
                        @Override
                        public void cancel() {}

                        @Override
                        public boolean isCancelled() {
                            return false;
                        }
                    };
                }

                @Override
                public Task runLater(Duration delay, Runnable task) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Task runRepeating(Duration delay, Duration period, Runnable task) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean isMainThread() {
                    return true;
                }
            };

    private static ApiConfig configWithKey(String key) {
        return new ApiConfig(
                "0.0.0.0",
                8080,
                List.of("https://kreiscraft.de"),
                null,
                Duration.ofSeconds(20),
                Duration.ofSeconds(10),
                Duration.ofSeconds(1),
                Duration.ofMinutes(2),
                key);
    }

    private static ApiConfig configDisabled() {
        return configWithKey("");
    }

    // -----------------------------------------------------------------------
    // GET /whitelist
    // -----------------------------------------------------------------------

    @Test
    void get_whitelist_adminKeyDisabled_returns503() {
        var whitelist = mock(WhitelistService.class);
        var app = Javalin.create();
        WhitelistRoutes.register(app, configDisabled(), whitelist, SYNC_SCHEDULER);

        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/whitelist");
            assertThat(response.code()).isEqualTo(503);
            assertThat(response.body().string()).contains("admin_api_disabled");
        });
    }

    @Test
    void get_whitelist_missingApiKeyHeader_returns401() {
        var whitelist = mock(WhitelistService.class);
        var app = Javalin.create();
        WhitelistRoutes.register(app, configWithKey("secret"), whitelist, SYNC_SCHEDULER);

        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/whitelist");
            assertThat(response.code()).isEqualTo(401);
        });
    }

    @Test
    void get_whitelist_wrongApiKey_returns401() {
        var whitelist = mock(WhitelistService.class);
        var app = Javalin.create();
        WhitelistRoutes.register(app, configWithKey("secret"), whitelist, SYNC_SCHEDULER);

        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/whitelist", req -> req.header("X-Kreiscraft-Api-Key", "wrong"));
            assertThat(response.code()).isEqualTo(401);
        });
    }

    @Test
    void get_whitelist_correctApiKey_returnsSortedNames_withoutNulls() {
        var whitelist = mock(WhitelistService.class);
        // Arrays.asList allows null elements; List.of does not.
        when(whitelist.allNames()).thenReturn(Arrays.asList("Zara", "alice", null, "Bob"));
        var app = Javalin.create();
        WhitelistRoutes.register(app, configWithKey("secret"), whitelist, SYNC_SCHEDULER);

        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/whitelist", req -> req.header("X-Kreiscraft-Api-Key", "secret"));
            assertThat(response.code()).isEqualTo(200);
            String body = response.body().string();
            // Sorted, nulls removed → ["Bob","Zara","alice"]
            assertThat(body).contains("\"Bob\"").contains("\"Zara\"").contains("\"alice\"");
            assertThat(body).doesNotContain("null");
            // Verify ordering: Bob before Zara, Zara before alice
            assertThat(body.indexOf("\"Bob\"")).isLessThan(body.indexOf("\"Zara\""));
            assertThat(body.indexOf("\"Zara\"")).isLessThan(body.indexOf("\"alice\""));
        });
    }

    // -----------------------------------------------------------------------
    // POST /whitelist
    // -----------------------------------------------------------------------

    @Test
    void post_whitelist_adminKeyDisabled_returns503() {
        var whitelist = mock(WhitelistService.class);
        var app = Javalin.create();
        WhitelistRoutes.register(app, configDisabled(), whitelist, SYNC_SCHEDULER);

        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/whitelist", Map.of("playerName", "Steve"));
            assertThat(response.code()).isEqualTo(503);
            assertThat(response.body().string()).contains("admin_api_disabled");
        });
    }

    @Test
    void post_whitelist_correctKey_validName_202_andCallsWhitelistServiceOnMain() {
        var whitelist = mock(WhitelistService.class);
        var app = Javalin.create();
        WhitelistRoutes.register(app, configWithKey("secret"), whitelist, SYNC_SCHEDULER);

        JavalinTest.test(app, (server, client) -> {
            var response = client.post(
                    "/whitelist",
                    Map.of("playerName", "Steve"),
                    req -> req.header("X-Kreiscraft-Api-Key", "secret"));
            assertThat(response.code()).isEqualTo(202);
            assertThat(response.body().string()).contains("whitelisted");
            verify(whitelist).addJavaPlayer("Steve");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"x; op y", "", "toolongnametoolongnametoolongname", "has space"})
    void post_whitelist_invalidName_400(String name) {
        var whitelist = mock(WhitelistService.class);
        var app = Javalin.create();
        WhitelistRoutes.register(app, configWithKey("secret"), whitelist, SYNC_SCHEDULER);

        JavalinTest.test(app, (server, client) -> {
            // Map.of forbids null but allows empty string, so use Arrays.asList-based map
            var body = new java.util.HashMap<String, String>();
            body.put("playerName", name);
            var response =
                    client.post("/whitelist", body, req -> req.header("X-Kreiscraft-Api-Key", "secret"));
            assertThat(response.code()).isEqualTo(400);
            verify(whitelist, never()).addJavaPlayer(name);
        });
    }

    @Test
    void post_whitelist_nullBody_400() {
        var whitelist = mock(WhitelistService.class);
        var app = Javalin.create();
        WhitelistRoutes.register(app, configWithKey("secret"), whitelist, SYNC_SCHEDULER);

        JavalinTest.test(app, (server, client) -> {
            // Empty JSON object → playerName deserializes as null → 400 with invalid_name body
            var response = client.post(
                    "/whitelist",
                    Map.of(),
                    req -> req.header("X-Kreiscraft-Api-Key", "secret"));
            assertThat(response.code()).isEqualTo(400);
            assertThat(response.body().string()).contains("invalid_name");
            verify(whitelist, never()).addJavaPlayer(org.mockito.ArgumentMatchers.any());
        });
    }

    @Test
    void post_whitelist_malformedJson_returns400WithInvalidBodyError() {
        var whitelist = mock(WhitelistService.class);
        var app = Javalin.create();
        WhitelistRoutes.register(app, configWithKey("secret"), whitelist, SYNC_SCHEDULER);

        JavalinTest.test(app, (server, client) -> {
            var response = client.request(
                    "/whitelist",
                    req -> req.post(RequestBody.create("not-json", MediaType.get("application/json")))
                            .header("X-Kreiscraft-Api-Key", "secret"));
            assertThat(response.code()).isEqualTo(400);
            assertThat(response.body().string()).contains("invalid_body");
        });
    }
}
