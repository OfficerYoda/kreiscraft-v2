package de.kreiscraft.modules.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.api.metrics.ServerMetricsCollector;
import de.kreiscraft.modules.api.verification.LinkGuiPrompt;
import de.kreiscraft.modules.api.verification.VerificationService;
import de.kreiscraft.modules.api.verification.WhitelistRequests;
import de.kreiscraft.modules.api.scene.SceneEvents;
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.shared.whitelist.WhitelistService;
import java.time.Clock;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Javalin HTTP/WebSocket API module.
 *
 * <p>Wires together {@link ApiServer}, {@link VerificationService}, {@link ServerMetricsCollector},
 * and {@link LinkGuiPrompt}, then starts the Javalin server on the configured host/port.
 *
 * <p><b>Config-fields-that-take-effect-only-on-restart:</b> {@link ApiConfig} parameters other
 * than {@code host}, {@code port}, and {@code corsOrigins} (e.g. {@code heartbeatInterval},
 * {@code asyncTimeout}, {@code metricsRefreshInterval}, {@code verificationTimeout}) are baked
 * into sub-services at construction time and cannot be hot-updated. A change to any of these
 * fields requires a plugin reload that triggers a full server restart via
 * {@link #onConfigReload(ConfigurationNode)}. This is a deliberate design choice — the complexity
 * of partially reconstructing sub-services is not worth it for rarely-changed operational tuning
 * parameters.
 */
public final class ApiModule implements Module {

    // ---- lifecycle state ----
    private ApiConfig config;
    private ApiServer server;
    private VerificationService verification;
    private ServerMetricsCollector metricsCollector;
    private SceneEvents scene;
    private WhitelistRequests whitelistRequests;

    // ---- stored from enable() for use in onConfigReload() ----
    private LinkGuiPrompt linkPrompt;
    private ChatBridge chatBridge;
    private WhitelistService whitelist;
    private MessageService messages;
    private Scheduler scheduler;
    private Server bukkitServer;
    private Logger logger;

    @Override
    public String id() {
        return "api";
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(ApiConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        this.chatBridge = ctx.services().get(ChatBridge.class);
        this.whitelist = ctx.services().get(WhitelistService.class);
        var gui = ctx.services().get(Gui.class);

        this.messages = ctx.messages();
        this.scheduler = ctx.scheduler();
        this.bukkitServer = ctx.plugin().getServer();
        this.logger = ctx.plugin().getLogger();

        this.verification =
                new VerificationService(scheduler, config.verificationTimeout(), Clock.systemUTC());
        this.metricsCollector =
                new ServerMetricsCollector(bukkitServer, scheduler, config.metricsRefreshInterval());
        this.linkPrompt = new LinkGuiPrompt(gui, messages);
        this.scene = new SceneEvents(bukkitServer, scheduler);
        ctx.events().register(scene);
        this.whitelistRequests = new WhitelistRequests(
                ctx.plugin().getDataFolder().toPath().resolve("whitelist-requests"),
                new ObjectMapper(), Clock.systemUTC());

        this.server = buildServer(config);
        this.server.start();

        // Publish so other modules (admin, monitoring) can query verification state.
        ctx.services().publish(VerificationService.class, verification);
    }

    @Override
    public void disable() {
        if (server != null) {
            server.stop();
        }
        if (verification != null) {
            verification.shutdown();
        }
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        ApiConfig newConfig;
        try {
            newConfig = moduleConfig.get(ApiConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
        boolean requiresRestart =
                !java.util.Objects.equals(newConfig.host(), config.host())
                        || newConfig.port() != config.port()
                        || !newConfig.corsOrigins().equals(config.corsOrigins());
        this.config = newConfig;
        if (requiresRestart && server != null) {
            server.stop();
            // Reconstruct the server with the updated config.
            // VerificationService and ServerMetricsCollector are NOT rebuilt here because
            // their tuning is baked in at construction and they are not hot-reloadable.
            // The VerificationService reference published during enable() remains valid.
            this.server = buildServer(newConfig);
            this.server.start();
        }
    }

    // ---- private helpers ----

    private ApiServer buildServer(ApiConfig cfg) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new ApiServer(
                cfg,
                metricsCollector,
                verification,
                linkPrompt,
                whitelist,
                chatBridge,
                messages,
                scheduler,
                bukkitServer,
                mapper,
                logger,
                scene,
                whitelistRequests);
    }
}
