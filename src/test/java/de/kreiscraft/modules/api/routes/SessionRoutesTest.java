package de.kreiscraft.modules.api.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kreiscraft.modules.api.ApiConfig;
import de.kreiscraft.modules.api.dto.InitializationData;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionRoutesTest {

    private static final String COOKIE_NAME = "website_user_uuid";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ApiConfig defaultConfig() {
        return new ApiConfig(
                "0.0.0.0",
                8080,
                List.of("https://kreiscraft.de"),
                new ApiConfig.SessionCookie(COOKIE_NAME, Duration.ofDays(30)),
                Duration.ofSeconds(20),
                Duration.ofSeconds(10),
                Duration.ofSeconds(1),
                Duration.ofMinutes(2),
                "test-key");
    }

    private static Javalin buildApp() {
        var app = Javalin.create();
        SessionRoutes.register(app, defaultConfig());
        return app;
    }

    @Test
    void get_initialize_withoutCookie_setsCookie_returnsStatusNew() {
        JavalinTest.test(buildApp(), (server, client) -> {
            var response = client.get("/initialize");
            assertThat(response.code()).isEqualTo(200);

            String setCookie = response.header("Set-Cookie");
            assertThat(setCookie).isNotNull();
            assertThat(setCookie).contains(COOKIE_NAME + "=");

            InitializationData data = MAPPER.readValue(response.body().string(), InitializationData.class);
            assertThat(data.status()).isEqualTo("new");
            assertThat(data.userId()).isNotBlank();
        });
    }

    @Test
    void get_initialize_withValidUuidCookie_returnsStatusExisting_withSameUserId_noSetCookie() {
        String existingUuid = "550e8400-e29b-41d4-a716-446655440000";
        JavalinTest.test(buildApp(), (server, client) -> {
            var response = client.get(
                    "/initialize",
                    req -> req.header("Cookie", COOKIE_NAME + "=" + existingUuid));
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.header("Set-Cookie")).isNull();

            InitializationData data = MAPPER.readValue(response.body().string(), InitializationData.class);
            assertThat(data.status()).isEqualTo("existing");
            assertThat(data.userId()).isEqualTo(existingUuid);
        });
    }

    @Test
    void get_initialize_withMalformedCookie_treatsAsNew_andSetsFreshCookie() {
        JavalinTest.test(buildApp(), (server, client) -> {
            var response = client.get(
                    "/initialize",
                    req -> req.header("Cookie", COOKIE_NAME + "=not-a-valid-uuid"));
            assertThat(response.code()).isEqualTo(200);

            String setCookie = response.header("Set-Cookie");
            assertThat(setCookie).isNotNull();
            assertThat(setCookie).contains(COOKIE_NAME + "=");

            InitializationData data = MAPPER.readValue(response.body().string(), InitializationData.class);
            assertThat(data.status()).isEqualTo("new");
            assertThat(data.userId()).isNotBlank();
        });
    }

    @Test
    void setCookie_hasHttpOnly_secure_false_sameSite_strict() {
        JavalinTest.test(buildApp(), (server, client) -> {
            var response = client.get("/initialize");
            String setCookie = response.header("Set-Cookie");
            assertThat(setCookie).isNotNull();
            assertThat(setCookie).containsIgnoringCase("HttpOnly");
            assertThat(setCookie).doesNotContainIgnoringCase("Secure");
            assertThat(setCookie).containsIgnoringCase("SameSite=Strict");
        });
    }
}
