package de.kreiscraft.modules.api.routes;

import de.kreiscraft.modules.api.metrics.ServerMetricsCollector;
import io.javalin.Javalin;

public final class PlayerRoutes {

    private PlayerRoutes() {}

    public static void register(Javalin app, ServerMetricsCollector metrics) {
        app.get("/players", ctx -> ctx.json(metrics.currentPlayers()));
    }
}
