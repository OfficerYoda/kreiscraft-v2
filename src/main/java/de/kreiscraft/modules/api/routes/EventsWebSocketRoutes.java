package de.kreiscraft.modules.api.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.api.scene.SceneEvents;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Public read-only scene stream. Reconnects always start with a fresh snapshot. */
public final class EventsWebSocketRoutes {
    private final SceneEvents scene;
    private final Scheduler scheduler;
    private final ObjectMapper mapper;
    private final Logger logger;
    private final Set<WsContext> clients = ConcurrentHashMap.newKeySet();
    private volatile boolean active;

    public EventsWebSocketRoutes(SceneEvents scene, Scheduler scheduler, ObjectMapper mapper, Logger logger) {
        this.scene = scene;
        this.scheduler = scheduler;
        this.mapper = mapper;
        this.logger = logger;
    }

    public void register(Javalin app) {
        active = true;
        scene.start(message -> clients.forEach(client -> send(client, message)));
        app.ws("/events", ws -> {
            ws.onConnect(ctx -> scheduler.runOnMain(() -> {
                if (active && ctx.session.isOpen()) {
                    // Add only after the snapshot, so live events cannot overtake it.
                    send(ctx, scene.snapshot());
                    clients.add(ctx);
                }
            }));
            ws.onClose(clients::remove);
            ws.onError(clients::remove);
            ws.onMessage(ctx -> ctx.closeSession(1008, "read-only endpoint"));
        });
    }

    public void shutdown() {
        active = false;
        scene.stop();
        clients.clear();
    }

    private void send(WsContext client, SceneEvents.Message message) {
        try {
            if (!client.session.isOpen()) {
                clients.remove(client);
                return;
            }
            client.send(mapper.writeValueAsString(message));
        } catch (Exception e) {
            clients.remove(client);
            logger.log(Level.FINE, "Failed to send scene message", e);
        }
    }
}
