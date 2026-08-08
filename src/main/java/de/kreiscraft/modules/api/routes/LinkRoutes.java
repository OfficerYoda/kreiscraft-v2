package de.kreiscraft.modules.api.routes;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.api.ApiConfig;
import de.kreiscraft.modules.api.dto.LinkedPlayerResponse;
import de.kreiscraft.modules.api.dto.LinkRequest;
import de.kreiscraft.modules.api.dto.PlayerData;
import de.kreiscraft.modules.api.metrics.ServerMetricsCollector;
import de.kreiscraft.modules.api.verification.LinkGuiPrompt;
import de.kreiscraft.modules.api.verification.VerificationService;
import de.kreiscraft.modules.api.verification.VerificationStatus;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Javalin routes for browser↔player linking.
 *
 * <ul>
 *   <li>{@code POST /link-player} — initiate a link request for a named online player.
 *   <li>{@code GET /linked-player} — poll the current link status for a browser session.
 *   <li>{@code POST /unlink-player} — remove an accepted link and notify the player.
 * </ul>
 *
 * <p>All Bukkit API access is dispatched through {@link Scheduler#runOnMain(Runnable)}.
 */
public final class LinkRoutes {

    private LinkRoutes() {}

    /**
     * Registers the three link routes on {@code app}.
     *
     * @param app          the Javalin instance to register routes on
     * @param config       API module config (provides cookie name)
     * @param verification the verification state store
     * @param linkPrompt   the in-game GUI prompt opened on the target player
     * @param metrics      server metrics collector for online player data
     * @param scheduler    scheduler used to run all Bukkit API calls on the main thread
     * @param server       the Bukkit server (never touched outside {@code runOnMain})
     * @param messages     message service for player notifications
     */
    public static void register(
            Javalin app,
            ApiConfig config,
            VerificationService verification,
            LinkGuiPrompt linkPrompt,
            ServerMetricsCollector metrics,
            Scheduler scheduler,
            Server server,
            MessageService messages) {

        String cookieName = config.sessionCookie().name();

        // POST /link-player
        app.post(
                "/link-player",
                ctx -> {
                    UUID browserUuid = parseCookieUuid(ctx, cookieName);
                    if (browserUuid == null) {
                        ctx.status(401).json(Map.of("error", "missing_session"));
                        return;
                    }

                    LinkRequest body;
                    try {
                        body = ctx.bodyAsClass(LinkRequest.class);
                    } catch (Exception e) {
                        ctx.status(400).json(Map.of("error", "invalid_body"));
                        return;
                    }
                    if (body.username() == null || body.username().isBlank()) {
                        ctx.status(400).json(Map.of("error", "missing_username"));
                        return;
                    }

                    String username = body.username();

                    ctx.future(
                            () -> {
                                CompletableFuture<Void> cf = new CompletableFuture<>();
                                scheduler.runOnMain(
                                        () -> {
                                            try {
                                                Player target = server.getPlayerExact(username);
                                                if (target == null) {
                                                    ctx.status(422)
                                                            .json(
                                                                    Map.of(
                                                                            "error",
                                                                            "player_offline"));
                                                    cf.complete(null);
                                                    return;
                                                }
                                                String browserDesc =
                                                        "browser "
                                                                + browserUuid
                                                                        .toString()
                                                                        .substring(0, 8);
                                                VerificationService.SendResult result =
                                                        verification.sendVerification(
                                                                browserUuid,
                                                                target.getName(),
                                                                target.getUniqueId(),
                                                                () -> linkPrompt.open(
                                                                        target,
                                                                        browserDesc,
                                                                        () -> verification
                                                                                .confirmVerification(
                                                                                        browserUuid),
                                                                        () -> verification
                                                                                .cancelVerification(
                                                                                        browserUuid)));
                                                switch (result) {
                                                    case SENT ->
                                                        ctx.status(202)
                                                                .json(
                                                                        Map.of(
                                                                                "status",
                                                                                "verification_sent"));
                                                    case ALREADY_PENDING ->
                                                        ctx.status(409)
                                                                .json(
                                                                        Map.of(
                                                                                "status",
                                                                                "already_pending"));
                                                    default ->
                                                        ctx.status(500)
                                                                .json(
                                                                        Map.of(
                                                                                "error",
                                                                                "unexpected_result"));
                                                }
                                                cf.complete(null);
                                            } catch (Exception e) {
                                                cf.completeExceptionally(e);
                                            }
                                        });
                                return cf;
                            });
                });

        // GET /linked-player
        app.get(
                "/linked-player",
                ctx -> {
                    UUID browserUuid = parseCookieUuid(ctx, cookieName);
                    if (browserUuid == null) {
                        ctx.status(401).json(Map.of("error", "missing_session"));
                        return;
                    }

                    VerificationStatus status = verification.getStatus(browserUuid);
                    LinkedPlayerResponse response;
                    if (status == null) {
                        response = LinkedPlayerResponse.notLinked();
                    } else {
                        response =
                                switch (status) {
                                    case PENDING -> LinkedPlayerResponse.pending();
                                    case DECLINED -> LinkedPlayerResponse.declined();
                                    case ACCEPTED -> {
                                        UUID playerUuid =
                                                verification.getLinkedPlayer(browserUuid);
                                        PlayerData data = metrics.playerByUuid(playerUuid);
                                        yield data != null
                                                ? LinkedPlayerResponse.accepted(data)
                                                : LinkedPlayerResponse.acceptedOffline();
                                    }
                                };
                    }
                    ctx.json(response);
                });

        // POST /unlink-player
        app.post(
                "/unlink-player",
                ctx -> {
                    UUID browserUuid = parseCookieUuid(ctx, cookieName);
                    if (browserUuid == null) {
                        ctx.status(401).json(Map.of("error", "missing_session"));
                        return;
                    }

                    UUID linkedUuid = verification.getLinkedPlayer(browserUuid);
                    if (linkedUuid == null) {
                        ctx.status(400).json(Map.of("error", "not_linked"));
                        return;
                    }

                    verification.unlink(browserUuid);

                    // Fire-and-forget: notify the player on the main thread if still online.
                    scheduler.runOnMain(
                            () -> {
                                Player player = server.getPlayer(linkedUuid);
                                if (player != null) {
                                    messages.send(player, "api.unlinked");
                                }
                            });

                    ctx.status(200).json(Map.of("status", "unlinked"));
                });
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Reads the named cookie and parses it as a UUID.
     *
     * @return the parsed {@link UUID}, or {@code null} if the cookie is absent or not a valid UUID.
     */
    private static UUID parseCookieUuid(Context ctx, String cookieName) {
        String value = ctx.cookie(cookieName);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
