package de.kreiscraft.modules.api.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kreiscraft.modules.api.verification.WhitelistRequests;
import de.kreiscraft.shared.whitelist.WhitelistService;
import de.kreiscraft.testsupport.MockScheduler;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import okhttp3.RequestBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WhitelistRequestRoutesTest {
    @TempDir
    Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void publicRequestsArePersistentCaseInsensitiveAndNeverMutateTheWhitelist() {
        var whitelist = mock(WhitelistService.class);
        when(whitelist.allNames()).thenReturn(List.of());
        var requests = new WhitelistRequests(directory, mapper, clock);
        var app = Javalin.create();
        WhitelistRequestRoutes.register(app, requests, whitelist, new MockScheduler());
        JavalinTest.test(app, (server, client) -> {
            assertThat(client.get("/whitelist/Notch").body().string()).contains("not_requested");
            var created = client.post("/whitelist/Notch", RequestBody.create(new byte[0], null));
            assertThat(created.code()).isEqualTo(202);
            var body = mapper.readTree(created.body().string());
            assertThat(body.get("status").asText()).isEqualTo("pending");
            assertThat(body.get("requestedAt").asText()).isEqualTo(clock.instant().toString());
            assertThat(body.has("resolvedAt")).isFalse();
            var repeated = client.post("/whitelist/NOTCH", RequestBody.create(new byte[0], null));
            assertThat(repeated.code()).isEqualTo(200);
            assertThat(mapper.readTree(repeated.body().string())).isEqualTo(body);
            var restarted = new WhitelistRequests(directory, mapper, clock);
            assertThat(restarted.get("notch", false, false).body()).containsEntry("playerName", "Notch")
                    .containsEntry("submissionId", body.get("submissionId").asText());
            when(whitelist.allNames()).thenReturn(List.of("NOTCH"));
            assertThat(client.get("/whitelist/notch").body().string()).contains("whitelisted");
            when(whitelist.allNames()).thenReturn(List.of());
            assertThat(client.get("/whitelist/notch").body().string()).contains("pending");
            assertThat(client.post("/whitelist/notch?reopen=true", RequestBody.create(new byte[0], null)).code())
                    .isEqualTo(200);
            assertThat(client.get("/whitelist/bad-name").code()).isEqualTo(400);
            assertThat(client.post("/whitelist/bad-name", RequestBody.create(new byte[0], null)).code()).isEqualTo(400);
        });
        verify(whitelist, never()).addJavaPlayer("Notch");
    }

    @Test
    void failedOrCorruptStorageReturns503WithoutOverwritingSubmissions() throws Exception {
        Files.writeString(directory.resolve("notch.json"), "broken");
        var whitelist = mock(WhitelistService.class);
        var app = Javalin.create();
        WhitelistRequestRoutes.register(app, new WhitelistRequests(directory, mapper, clock), whitelist,
                new MockScheduler());
        JavalinTest.test(app, (server, client) -> {
            assertThat(client.get("/whitelist/Notch").code()).isEqualTo(503);
            assertThat(client.post("/whitelist/Notch", RequestBody.create(new byte[0], null)).body().string())
                    .contains("storage_unavailable");
        });
        assertThat(Files.readString(directory.resolve("notch.json"))).isEqualTo("broken");
    }

    @Test
    void quotaLimitsOnlyNewNamesAndWhitelistedNamesDoNotCreateRequests() throws Exception {
        var requests = new WhitelistRequests(directory, mapper, clock);
        assertThat(requests.get("Alice", true, true).body()).containsEntry("status", "whitelisted");
        assertThat(Files.exists(directory.resolve("alice.json"))).isFalse();
        for (int i = 0; i < 60; i++) {
            assertThat(requests.get("Player" + i, true, false).statusCode()).isEqualTo(202);
        }
        assertThat(requests.get("Player60", true, false).statusCode()).isEqualTo(429);
        assertThat(requests.get("Player0", true, false).statusCode()).isEqualTo(200);
        assertThat(requests.get("Unknown", false, false).statusCode()).isEqualTo(200);
    }
}
