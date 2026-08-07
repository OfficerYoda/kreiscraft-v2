package de.kreiscraft.modules.api.routes;

import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.api.ApiConfig;
import de.kreiscraft.modules.api.dto.WhitelistRequest;
import de.kreiscraft.shared.whitelist.WhitelistService;
import io.javalin.Javalin;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class WhitelistRoutes {

    private WhitelistRoutes() {}

    static final Pattern MC_NAME = Pattern.compile("^[a-zA-Z0-9_]{1,16}$");

    public static void register(Javalin app, ApiConfig config, WhitelistService whitelist, Scheduler scheduler) {
        app.get("/whitelist", ctx -> {
            if (config.adminApiKey().isBlank()) {
                ctx.status(503).json(Map.of("error", "admin_api_disabled"));
                return;
            }
            String key = ctx.header("X-Kreiscraft-Api-Key");
            if (key == null || !key.equals(config.adminApiKey())) {
                ctx.status(401);
                return;
            }
            ctx.json(whitelist.allNames().stream().filter(Objects::nonNull).sorted().toList());
        });

        app.post("/whitelist", ctx -> {
            if (config.adminApiKey().isBlank()) {
                ctx.status(503).json(Map.of("error", "admin_api_disabled"));
                return;
            }
            String key = ctx.header("X-Kreiscraft-Api-Key");
            if (key == null || !key.equals(config.adminApiKey())) {
                ctx.status(401);
                return;
            }
            WhitelistRequest req = ctx.bodyAsClass(WhitelistRequest.class);
            String playerName = req == null ? null : req.playerName();
            if (playerName == null || playerName.isBlank()) {
                ctx.status(400);
                return;
            }
            if (!MC_NAME.matcher(playerName).matches()) {
                ctx.status(400).json(Map.of("error", "invalid_name"));
                return;
            }
            String name = playerName;
            CompletableFuture<Void> future = new CompletableFuture<>();
            scheduler.runOnMain(() -> {
                whitelist.addJavaPlayer(name);
                future.complete(null);
            });
            ctx.future(() -> future.thenRun(() -> ctx.status(202).json(Map.of("status", "whitelisted"))));
        });
    }
}
