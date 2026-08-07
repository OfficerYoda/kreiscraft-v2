package de.kreiscraft.modules.api.routes;

import de.kreiscraft.modules.api.metrics.ServerMetricsCollector;
import io.javalin.Javalin;

public final class StatusRoutes {

    private StatusRoutes() {}

    public static void register(Javalin app, ServerMetricsCollector metrics) {
        app.get("/status", ctx -> ctx.json(metrics.currentMetrics()));
    }
}
