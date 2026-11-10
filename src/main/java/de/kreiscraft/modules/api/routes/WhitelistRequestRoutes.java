package de.kreiscraft.modules.api.routes;

import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.api.verification.WhitelistRequests;
import de.kreiscraft.shared.whitelist.WhitelistService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class WhitelistRequestRoutes {
    private WhitelistRequestRoutes() {}

    public static void register(Javalin app, WhitelistRequests requests, WhitelistService whitelist,
            Scheduler scheduler) {
        app.get("/whitelist/{playerName}", ctx -> handle(ctx, false, requests, whitelist, scheduler));
        app.post("/whitelist/{playerName}", ctx -> handle(ctx, true, requests, whitelist, scheduler));
    }

    private static void handle(Context ctx, boolean submit, WhitelistRequests requests,
            WhitelistService whitelist, Scheduler scheduler) {
        String name = ctx.pathParam("playerName");
        if (!WhitelistRoutes.MC_NAME.matcher(name).matches()) {
            ctx.status(400).json(Map.of("error", "invalid_name"));
            return;
        }
        CompletableFuture<WhitelistRequests.Result> response = new CompletableFuture<>();
        scheduler.runOnMain(() -> {
            try {
                // Avoid getOfflinePlayer's profile lookup for untrusted public names.
                boolean whitelisted = whitelist.allNames().stream().anyMatch(name::equalsIgnoreCase);
                scheduler.runAsync(() -> {
                    try {
                        response.complete(requests.get(name, submit, whitelisted));
                    } catch (IOException e) {
                        response.complete(new WhitelistRequests.Result(503, Map.of("error", "storage_unavailable")));
                    } catch (RuntimeException e) {
                        response.completeExceptionally(e);
                    }
                });
            } catch (RuntimeException e) {
                response.completeExceptionally(e);
            }
        });
        ctx.future(() -> response.thenAccept(result -> ctx.status(result.statusCode()).json(result.body())));
    }
}
