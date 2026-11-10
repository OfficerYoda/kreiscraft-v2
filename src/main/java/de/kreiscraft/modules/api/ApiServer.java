package de.kreiscraft.modules.api;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.api.metrics.ServerMetricsCollector;
import de.kreiscraft.modules.api.routes.ChatWebSocketRoutes;
import de.kreiscraft.modules.api.routes.LinkRoutes;
import de.kreiscraft.modules.api.routes.PlayerRoutes;
import de.kreiscraft.modules.api.routes.SessionRoutes;
import de.kreiscraft.modules.api.routes.StatusRoutes;
import de.kreiscraft.modules.api.routes.WhitelistRoutes;
import de.kreiscraft.modules.api.routes.WhitelistRequestRoutes;
import de.kreiscraft.modules.api.routes.EventsWebSocketRoutes;
import de.kreiscraft.modules.api.scene.SceneEvents;
import de.kreiscraft.modules.api.verification.WhitelistRequests;
import de.kreiscraft.modules.api.verification.LinkGuiPrompt;
import de.kreiscraft.modules.api.verification.VerificationService;
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.whitelist.WhitelistService;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import java.util.logging.Logger;
import org.bukkit.Server;

/**
 * Owns the Javalin HTTP/WebSocket server lifecycle.
 *
 * <p>Each call to {@link #start()} creates a new {@link Javalin} instance, mounts all routes, and
 * binds to the host/port in {@link ApiConfig}. Call {@link #stop()} to tear down cleanly.
 *
 * <p><b>Config-fields-that-take-effect-only-on-restart:</b> {@link ApiConfig} parameters other
 * than host, port, and corsOrigins (e.g. heartbeatInterval, asyncTimeout,
 * metricsRefreshInterval, verificationTimeout) are baked in at construction time of the
 * sub-services and take effect only when the server is fully restarted via
 * {@link de.kreiscraft.modules.api.ApiModule#onConfigReload}. This is a documented
 * constraint — see {@link ApiModule} Javadoc.
 */
public final class ApiServer {

    private final ApiConfig config;
    private final ServerMetricsCollector metrics;
    private final VerificationService verification;
    private final LinkGuiPrompt linkPrompt;
    private final WhitelistService whitelist;
    private final ChatBridge chatBridge;
    private final MessageService messages;
    private final Scheduler scheduler;
    private final Server bukkitServer;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final Logger logger;
    private final SceneEvents scene;
    private final WhitelistRequests whitelistRequests;

    private Javalin app;
    private ChatWebSocketRoutes chatRoutes;
    private EventsWebSocketRoutes eventsRoutes;

    public ApiServer(
            ApiConfig config,
            ServerMetricsCollector metrics,
            VerificationService verification,
            LinkGuiPrompt linkPrompt,
            WhitelistService whitelist,
            ChatBridge chatBridge,
            MessageService messages,
            Scheduler scheduler,
            Server bukkitServer,
            com.fasterxml.jackson.databind.ObjectMapper mapper,
            Logger logger,
            SceneEvents scene,
            WhitelistRequests whitelistRequests) {
        this.config = config;
        this.metrics = metrics;
        this.verification = verification;
        this.linkPrompt = linkPrompt;
        this.whitelist = whitelist;
        this.chatBridge = chatBridge;
        this.messages = messages;
        this.scheduler = scheduler;
        this.bukkitServer = bukkitServer;
        this.mapper = mapper;
        this.logger = logger;
        this.scene = scene;
        this.whitelistRequests = whitelistRequests;
    }

    /**
     * Starts the metrics collector and the Javalin server, mounting all routes.
     *
     * <p>Must be called at most once per instance (or after a matching {@link #stop()}).
     */
    public void start() {
        metrics.start();

        this.app =
                Javalin.create(
                        cfg -> {
                            cfg.bundledPlugins.enableCors(
                                    cors -> cors.addRule(
                                            r -> {
                                                r.allowCredentials = true;
                                                config.corsOrigins()
                                                        .forEach(r::allowHost);
                                            }));
                            cfg.jsonMapper(new JavalinJackson(mapper, true));
                            cfg.http.asyncTimeout = config.asyncTimeout().toMillis();
                        });

        StatusRoutes.register(app, metrics);
        PlayerRoutes.register(app, metrics);
        SessionRoutes.register(app, config);
        LinkRoutes.register(
                app, config, verification, linkPrompt, metrics, scheduler, bukkitServer, messages);
        WhitelistRoutes.register(app, config, whitelist, scheduler);
        WhitelistRequestRoutes.register(app, whitelistRequests, whitelist, scheduler);

        // Javalin's HTTP CORS plugin does not authorize WebSocket upgrades.
        app.wsBeforeUpgrade(ctx -> {
            if ((ctx.path().equals("/chat") || ctx.path().equals("/events"))
                    && ctx.header("Origin") != null && !config.corsOrigins().contains(ctx.header("Origin"))) {
                throw new io.javalin.http.ForbiddenResponse("Origin is not allowed");
            }
        });

        this.chatRoutes =
                new ChatWebSocketRoutes(config, chatBridge, messages, scheduler, mapper, logger);
        chatRoutes.register(app);
        this.eventsRoutes = new EventsWebSocketRoutes(scene, scheduler, mapper, logger);
        eventsRoutes.register(app);

        app.start(config.host(), config.port());
    }

    /**
     * Shuts down the WebSocket routes first (stops heartbeat executor and unsubscribes from
     * {@link ChatBridge}), then stops the Javalin server, then stops the metrics collector.
     *
     * <p>Idempotent — safe to call if {@link #start()} was never called.
     */
    public void stop() {
        if (eventsRoutes != null) {
            eventsRoutes.shutdown();
            eventsRoutes = null;
        }
        if (chatRoutes != null) {
            chatRoutes.shutdown();
            chatRoutes = null;
        }
        if (app != null) {
            app.stop();
            app = null;
        }
        metrics.stop();
    }

    /**
     * Returns the actual TCP port the server is bound to. Only meaningful after {@link #start()}
     * has returned successfully. Useful in tests when starting on an ephemeral port.
     */
    public int port() {
        return app != null ? app.port() : -1;
    }
}
