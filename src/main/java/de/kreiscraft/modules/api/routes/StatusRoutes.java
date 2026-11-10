package de.kreiscraft.modules.api.routes;

import de.kreiscraft.modules.api.metrics.ServerMetricsCollector;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

public final class StatusRoutes {

    private StatusRoutes() {}

    public static void register(Javalin app, ServerMetricsCollector metrics) {
        app.get("/ping", ctx -> {
            ctx.header("Cache-Control", "no-store");
            // Preserve errors already set by the CORS before-handler.
            if (ctx.status() == HttpStatus.OK) {
                ctx.status(204);
            }
        });
        app.get("/status", ctx -> ctx.json(metrics.currentMetrics()));
    }
}
