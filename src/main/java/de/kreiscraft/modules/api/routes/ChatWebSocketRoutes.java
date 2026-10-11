package de.kreiscraft.modules.api.routes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.api.ApiConfig;
import de.kreiscraft.modules.api.dto.WebSocketMessage;
import de.kreiscraft.modules.api.dto.WebSocketMessageType;
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.chat.ChatBridgeEvent;
import de.kreiscraft.shared.chat.ChatSource;
import java.time.Instant;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

/**
 * WebSocket {@code /chat} endpoint. Bridges in-game chat (via {@link ChatBridge}) to browser
 * clients and forwards browser messages back to in-game chat via {@link MessageService}.
 *
 * <p>Threading: Javalin delivers WS events on a Jetty worker thread. All Bukkit API calls (e.g.
 * {@link MessageService#broadcast}) MUST be dispatched via {@link Scheduler#runOnMain} — never
 * called directly from a WS thread.
 */
public final class ChatWebSocketRoutes {

    private final ApiConfig config;
    private final ChatBridge chatBridge;
    private final MessageService messages;
    private final Scheduler scheduler;
    private final ObjectMapper mapper;
    private final Logger logger;

    private final Set<WsContext> connectedClients = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService heartbeat;
    private volatile ChatBridge.Subscription chatSubscription;

    /**
     * Production constructor. Creates a daemon heartbeat thread named
     * {@code kreiscraft-api-ws-heartbeat}.
     */
    public ChatWebSocketRoutes(
            ApiConfig config,
            ChatBridge chatBridge,
            MessageService messages,
            Scheduler scheduler,
            ObjectMapper mapper,
            Logger logger) {
        this(
                config,
                chatBridge,
                messages,
                scheduler,
                mapper,
                logger,
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            var t = new Thread(r, "kreiscraft-api-ws-heartbeat");
                            t.setDaemon(true);
                            return t;
                        }));
    }

    /**
     * Package-private constructor for tests — accepts an injected {@link ScheduledExecutorService}
     * so heartbeat scheduling can be verified without real threads.
     */
    ChatWebSocketRoutes(
            ApiConfig config,
            ChatBridge chatBridge,
            MessageService messages,
            Scheduler scheduler,
            ObjectMapper mapper,
            Logger logger,
            ScheduledExecutorService heartbeatExecutor) {
        this.config = config;
        this.chatBridge = chatBridge;
        this.messages = messages;
        this.scheduler = scheduler;
        this.mapper = mapper;
        this.logger = logger;
        this.heartbeat = heartbeatExecutor;
    }

    /**
     * Registers the {@code /chat} WebSocket endpoint on {@code app}, starts the heartbeat, and
     * subscribes to {@link ChatBridge}.
     */
    public void register(Javalin app) {
        long interval = config.heartbeatInterval().toMillis();
        heartbeat.scheduleAtFixedRate(this::sendPing, interval, interval, TimeUnit.MILLISECONDS);

        chatSubscription =
                chatBridge.subscribe(
                        event -> {
                            if (event.source() != ChatSource.WEB) {
                                broadcastToWsClients(new WebSocketMessage(
                                        WebSocketMessageType.CHAT, renderChatEventForWeb(event)));
                            }
                        });

        app.ws(
                "/chat",
                ws -> {
                    ws.onConnect(this::handleConnect);
                    ws.onMessage(ctx -> handleMessage(ctx, ctx.message()));
                    ws.onClose(this::handleClose);
                });
    }

    /**
     * Idempotent shutdown. Stops the heartbeat executor and unsubscribes from {@link ChatBridge}.
     * Safe to call multiple times.
     */
    public void shutdown() {
        ChatBridge.Subscription sub = chatSubscription;
        if (sub != null) {
            sub.unsubscribe();
            chatSubscription = null;
        }
        heartbeat.shutdown();
    }

    /** Package-private for tests. */
    int connectedClientCount() {
        return connectedClients.size();
    }

    // -------------------------------------------------------------------------
    // Package-private handlers — extracted from lambdas for testability
    // -------------------------------------------------------------------------

    /** Adds the client to the connected set and sends the handshake VERIFICATION message. */
    void handleConnect(WsContext ctx) {
        connectedClients.add(ctx);
        sendToClient(
                ctx,
                new WebSocketMessage(WebSocketMessageType.VERIFICATION, "Connection successful."));
    }

    /**
     * Processes an incoming raw JSON message from a browser client.
     *
     * <ul>
     *   <li>{@code PING} → reply {@code PONG}
     *   <li>{@code PONG} → ignored
     *   <li>{@code CHAT} → dispatch {@link MessageService#broadcast} on the main thread; echo to
     *       other connected clients
     *   <li>{@code VERIFICATION}, {@code EXCEPTION} → ignored
     *   <li>Malformed JSON → log at FINE and reply {@code EXCEPTION}
     * </ul>
     */
    void handleMessage(WsContext ctx, String raw) {
        WebSocketMessage msg;
        try {
            msg = mapper.readValue(raw, WebSocketMessage.class);
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to parse WebSocket message", e);
            sendToClient(ctx, new WebSocketMessage(WebSocketMessageType.EXCEPTION, "bad message"));
            return;
        }

        switch (msg.type()) {
            case PING -> sendToClient(
                    ctx, new WebSocketMessage(WebSocketMessageType.PONG, "heartbeat pong"));
            case PONG -> {
                // ignore
            }
            case CHAT -> {
                // Bukkit API must run on the main thread — schedule via Scheduler
                scheduler.runOnMain(
                        () -> {
                            Component rendered =
                                    messages.render("api.chat-format-from-web", "content", msg.content());
                            messages.broadcast("api.chat-format-from-web", "content", msg.content());
                            chatBridge.publish(new ChatBridgeEvent(
                                    ChatSource.WEB,
                                    null,
                                    "WEB",
                                    rendered,
                                    msg.content(),
                                    Instant.now()));
                        });
                // Echo to OTHER connected clients (sender's UI already showed it optimistically)
                broadcastToWsClientsExcept(
                        ctx, new WebSocketMessage(WebSocketMessageType.CHAT, msg.content()));
            }
            case VERIFICATION, EXCEPTION -> {
                // ignore
            }
        }
    }

    /** Removes the client from the connected set. */
    void handleClose(WsContext ctx) {
        connectedClients.remove(ctx);
    }

    // -------------------------------------------------------------------------
    // Package-private for tests
    // -------------------------------------------------------------------------

    /**
     * Renders a {@link ChatBridgeEvent} for the web frontend using the legacy {@code §}-prefixed
     * colour scheme. Non-{@link TextComponent} roots fall back to {@link ChatBridgeEvent#plainText}
     * to prevent {@link ClassCastException} on {@code TranslatableComponent} children.
     */
    String renderChatEventForWeb(ChatBridgeEvent event) {
        Component component = event.rendered();
        if (component instanceof TextComponent textComponent) {
            return textComponentToColoredString(textComponent);
        }
        // Fallback: use pre-computed plain text; never throws
        String plain = event.plainText();
        return (plain != null) ? plain : component.toString();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void sendPing() {
        // Drop sessions whose underlying Jetty session has been closed
        connectedClients.removeIf(
                ctx -> {
                    try {
                        return !ctx.session.isOpen();
                    } catch (Exception e) {
                        // Null / inaccessible session → treat as closed
                        return true;
                    }
                });
        connectedClients.forEach(
                ctx -> sendToClient(
                        ctx,
                        new WebSocketMessage(WebSocketMessageType.PING, "heartbeat ping")));
    }

    private void broadcastToWsClients(WebSocketMessage msg) {
        connectedClients.forEach(ctx -> sendToClient(ctx, msg));
    }

    private void broadcastToWsClientsExcept(WsContext except, WebSocketMessage msg) {
        connectedClients.stream()
                .filter(ctx -> !ctx.equals(except))
                .forEach(ctx -> sendToClient(ctx, msg));
    }

    private void sendToClient(WsContext ctx, WebSocketMessage msg) {
        try {
            ctx.send(mapper.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            logger.log(Level.WARNING, "Failed to serialize WebSocket message", e);
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to send WebSocket message to client", e);
        }
    }

    /**
     * Ported verbatim from legacy {@code ChatWebsocketHandler.java}:
     * {@code de.officeryoda.kreiscraftApi.ChatWebsocketHandler#textComponentToColoredString}.
     *
     * <p>Keeps the {@code §}-prefixed colour scheme for zero-downtime frontend compatibility.
     * Migration to MiniMessage may happen in the future.
     *
     * <p>The original code cast every child to {@link TextComponent} unconditionally, which threw
     * {@link ClassCastException} on {@code TranslatableComponent} children. This
     * port adds an {@code instanceof} guard to skip non-{@link TextComponent} children safely.
     */
    private String textComponentToColoredString(TextComponent textComponent) {
        ArrayList<TextComponent> components = new ArrayList<>();
        components.add(textComponent);
        for (Component child : textComponent.children()) {
            // instanceof guard: skip non-TextComponent children (e.g. TranslatableComponent) to avoid ClassCastException
            if (child instanceof TextComponent tc) {
                components.add(tc);
            }
        }

        StringBuilder coloredString = new StringBuilder();
        for (TextComponent comp : components) {
            String colorPrefix = getColorPrefix(comp.color());
            coloredString.append(colorPrefix).append(comp.content());
        }

        return coloredString.toString();
    }

    private String getColorPrefix(TextColor color) {
        // § cannot be typed in Minecraft chat — use as escape character (legacy frontend protocol)
        String colorPrefix = "§";
        if (color != null) {
            colorPrefix += color.asHexString(); // e.g. §#ff5500
        } else {
            colorPrefix += "§"; // no color → double escape
        }
        return colorPrefix;
    }
}
