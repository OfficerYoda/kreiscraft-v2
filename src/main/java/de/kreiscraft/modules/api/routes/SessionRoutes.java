package de.kreiscraft.modules.api.routes;

import de.kreiscraft.modules.api.ApiConfig;
import de.kreiscraft.modules.api.dto.InitializationData;
import io.javalin.Javalin;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;
import java.util.UUID;

public final class SessionRoutes {

    private SessionRoutes() {}

    public static void register(Javalin app, ApiConfig config) {
        app.get(
                "/initialize",
                ctx -> {
                    String cookieName = config.sessionCookie().name();
                    String cookieValue = ctx.cookie(cookieName);

                    if (cookieValue != null) {
                        try {
                            UUID.fromString(cookieValue);
                            ctx.json(new InitializationData("existing", cookieValue));
                            return;
                        } catch (IllegalArgumentException ignored) {
                            // malformed — fall through to issue a new cookie
                        }
                    }

                    String newUuid = UUID.randomUUID().toString();
                    Cookie cookie = new Cookie(cookieName, newUuid);
                    cookie.setPath("/");
                    cookie.setHttpOnly(true);
                    cookie.setSecure(false);
                    cookie.setSameSite(SameSite.STRICT);
                    cookie.setMaxAge((int) config.sessionCookie().maxAge().toSeconds());
                    ctx.cookie(cookie);
                    ctx.json(new InitializationData("new", newUuid));
                });
    }
}
