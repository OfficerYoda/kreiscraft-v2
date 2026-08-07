package de.kreiscraft.modules.api.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.api.ApiConfig;
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.chat.ChatBridgeEvent;
import de.kreiscraft.testsupport.MockScheduler;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link ChatWebSocketRoutes}.
 *
 * <p><b>WsContext strategy — Mockito-only:</b> Mockito 5 activates the inline mock maker by
 * default, which intercepts all method calls including {@code final} methods. This means
 * {@code ctx.send(String)} on a mocked {@link WsContext} is intercepted (returns no-op) and can be
 * verified directly via {@code verify(ctx).send(anyString())}. No {@link Session} /
 * {@code RemoteEndpoint} chain is needed for send-verification tests.
 *
 * <p>The exception is test 9 (heartbeat drops closed sessions), which exercises
 * {@code ctx.session.isOpen()}. Because {@code session} is a {@code public final} <em>field</em>
 * (not a method), the inline mock maker cannot intercept field reads. For those mocks we use
 * {@code withSettings().useConstructor(sessionId, session)} so that the {@link WsContext}
 * constructor is invoked and the field is properly set to a {@link Session} mock with a stubbed
 * {@code isOpen()} return value.
 *
 * <p>A package-private test constructor on {@link ChatWebSocketRoutes} accepts an injected
 * {@link ScheduledExecutorService} so heartbeat scheduling can be verified without real daemon
 * threads.
 */
class ChatWebSocketRoutesTest {

    // -------------------------------------------------------------------------
    // Test infrastructure helpers
    // -------------------------------------------------------------------------

    private static ApiConfig defaultConfig() {
        // Only heartbeatInterval matters for these tests; compact constructor sets defaults.
        return new ApiConfig(null, 0, null, null, Duration.ofMillis(20_000), null, null, null, null);
    }

    /** Creates a simple WsContext mock (inline mock maker intercepts send()). */
    private static WsContext simpleMockCtx() {
        return mock(WsContext.class);
    }

    /**
     * Creates a WsContext mock that has a properly-initialised {@code session} field (needed when
     * code accesses {@code ctx.session.isOpen()}).
     */
    private static WsContext mockCtxWithSession(boolean sessionOpen) {
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(sessionOpen);
        return mock(
                WsContext.class,
                withSettings().useConstructor("ts-" + UUID.randomUUID(), session));
    }

    private ChatWebSocketRoutes makeRoutes(ChatBridge bridge, MockScheduler scheduler) {
        return new ChatWebSocketRoutes(
                defaultConfig(),
                bridge,
                mock(MessageService.class),
                scheduler,
                new ObjectMapper(),
                mock(Logger.class));
    }

    private ChatWebSocketRoutes makeRoutes(
            ChatBridge bridge, MockScheduler scheduler, MessageService messages) {
        return new ChatWebSocketRoutes(
                defaultConfig(),
                bridge,
                messages,
                scheduler,
                new ObjectMapper(),
                mock(Logger.class));
    }

    private ChatWebSocketRoutes makeRoutesWithExecutor(
            ChatBridge bridge,
            MockScheduler scheduler,
            MessageService messages,
            ScheduledExecutorService executor) {
        return new ChatWebSocketRoutes(
                defaultConfig(),
                bridge,
                messages,
                scheduler,
                new ObjectMapper(),
                mock(Logger.class),
                executor);
    }

    // -------------------------------------------------------------------------
    // Test 1 — onConnect sends VERIFICATION handshake and adds to connected set
    // -------------------------------------------------------------------------

    @Test
    void onConnect_sendsVerificationHandshake_andAddsToConnectedSet() throws Exception {
        ChatBridge bridge = mock(ChatBridge.class);
        when(bridge.subscribe(any())).thenReturn(mock(ChatBridge.Subscription.class));
        ChatWebSocketRoutes routes = makeRoutes(bridge, new MockScheduler());

        WsContext ctx = simpleMockCtx();
        routes.handleConnect(ctx);

        assertThat(routes.connectedClientCount()).isEqualTo(1);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).send(captor.capture());
        assertThat(captor.getValue())
                .contains("\"type\":\"VERIFICATION\"")
                .contains("Connection successful.");
    }

    // -------------------------------------------------------------------------
    // Test 2 — onClose removes client from connected set
    // -------------------------------------------------------------------------

    @Test
    void onClose_removesFromConnectedSet() {
        ChatWebSocketRoutes routes =
                makeRoutes(mock(ChatBridge.class), new MockScheduler());

        WsContext ctx = simpleMockCtx();
        routes.handleConnect(ctx);
        assertThat(routes.connectedClientCount()).isEqualTo(1);

        routes.handleClose(ctx);
        assertThat(routes.connectedClientCount()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Test 3 — PING message triggers PONG reply
    // -------------------------------------------------------------------------

    @Test
    void onMessage_pingReply() throws Exception {
        ChatWebSocketRoutes routes = makeRoutes(mock(ChatBridge.class), new MockScheduler());

        WsContext ctx = simpleMockCtx();
        routes.handleMessage(ctx, "{\"type\":\"PING\",\"content\":\"x\"}");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).send(captor.capture());
        assertThat(captor.getValue())
                .contains("\"type\":\"PONG\"")
                .contains("heartbeat pong");
    }

    // -------------------------------------------------------------------------
    // Test 4 — CHAT dispatches broadcast on main thread and echoes to other clients
    // -------------------------------------------------------------------------

    @Test
    void onMessage_chat_dispatchesBroadcastOnMainThread_andEchoesToOtherClients()
            throws Exception {
        MockScheduler scheduler = new MockScheduler();
        MessageService mockMessages = mock(MessageService.class);
        ChatWebSocketRoutes routes = makeRoutes(mock(ChatBridge.class), scheduler, mockMessages);

        WsContext senderCtx = simpleMockCtx();
        WsContext receiverCtx = simpleMockCtx();
        routes.handleConnect(senderCtx);
        routes.handleConnect(receiverCtx);

        routes.handleMessage(senderCtx, "{\"type\":\"CHAT\",\"content\":\"hello world\"}");

        // MockScheduler.runOnMain() executes the task immediately
        verify(mockMessages).broadcast("api.chat-format.from-web", "content", "hello world");

        // Receiver: VERIFICATION (from handleConnect) + CHAT echo = 2 sends
        ArgumentCaptor<String> receiverCaptor = ArgumentCaptor.forClass(String.class);
        verify(receiverCtx, times(2)).send(receiverCaptor.capture());
        assertThat(receiverCaptor.getAllValues())
                .anyMatch(s -> s.contains("\"type\":\"CHAT\"") && s.contains("hello world"));

        // Sender: only VERIFICATION on connect, NO CHAT echo
        verify(senderCtx, never()).send(argThat((String s) -> s.contains("\"type\":\"CHAT\"")));
    }

    // -------------------------------------------------------------------------
    // Test 5 — PONG message is silently ignored
    // -------------------------------------------------------------------------

    @Test
    void onMessage_pong_ignored() {
        ChatWebSocketRoutes routes = makeRoutes(mock(ChatBridge.class), new MockScheduler());

        WsContext ctx = simpleMockCtx();
        routes.handleMessage(ctx, "{\"type\":\"PONG\",\"content\":\"x\"}");

        verify(ctx, never()).send(anyString());
    }

    // -------------------------------------------------------------------------
    // Test 6 — Malformed JSON triggers EXCEPTION reply
    // -------------------------------------------------------------------------

    @Test
    void onMessage_malformedJson_sendsException() throws Exception {
        ChatWebSocketRoutes routes = makeRoutes(mock(ChatBridge.class), new MockScheduler());

        WsContext ctx = simpleMockCtx();
        routes.handleMessage(ctx, "not-valid-json-{{{");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).send(captor.capture());
        assertThat(captor.getValue())
                .contains("\"type\":\"EXCEPTION\"")
                .contains("bad message");
    }

    // -------------------------------------------------------------------------
    // Test 7 — ChatBridge publish broadcasts CHAT to all connected clients
    // -------------------------------------------------------------------------

    @Test
    void chatBridgePublish_broadcastsToAllConnectedClients() {
        ChatBridge realBridge = new ChatBridge();
        ChatWebSocketRoutes routes = makeRoutes(realBridge, new MockScheduler());

        // register() subscribes to the real bridge before calling app.ws() — mock Javalin is fine
        Javalin mockApp = mock(Javalin.class);
        routes.register(mockApp);

        WsContext client1 = simpleMockCtx();
        WsContext client2 = simpleMockCtx();
        routes.handleConnect(client1);
        routes.handleConnect(client2);

        ChatBridgeEvent event =
                new ChatBridgeEvent(
                        UUID.randomUUID(),
                        "Alice",
                        Component.text("hello"),
                        "hello",
                        Instant.now());
        realBridge.publish(event);

        // Both clients must receive a CHAT message from the bridge publish
        verify(client1, atLeastOnce()).send(argThat((String s) -> s.contains("\"type\":\"CHAT\"")));
        verify(client2, atLeastOnce()).send(argThat((String s) -> s.contains("\"type\":\"CHAT\"")));
    }

    // -------------------------------------------------------------------------
    // Test 8 — TranslatableComponent does not throw (regression for legacy B-03)
    // -------------------------------------------------------------------------

    @Test
    void chatBridgePublish_withTranslatableComponent_doesNotThrow() {
        ChatWebSocketRoutes routes = makeRoutes(mock(ChatBridge.class), new MockScheduler());

        Component translatable = Component.translatable("chat.type.text");
        ChatBridgeEvent event =
                new ChatBridgeEvent(
                        UUID.randomUUID(),
                        "Bob",
                        translatable,
                        "plain fallback text",
                        Instant.now());

        // Must not throw; must return the plain-text fallback (non-null, non-empty)
        String result = routes.renderChatEventForWeb(event);

        assertThat(result).isNotNull().isNotEmpty();
        assertThat(result).isEqualTo("plain fallback text");
    }

    // -------------------------------------------------------------------------
    // Test 9 — Heartbeat is scheduled at configured interval; drops closed sessions
    // -------------------------------------------------------------------------

    @Test
    void heartbeat_scheduledAtConfiguredInterval_dropsClosedSessions() throws Exception {
        ScheduledExecutorService mockExecutor = mock(ScheduledExecutorService.class);
        ChatWebSocketRoutes routes =
                makeRoutesWithExecutor(
                        mock(ChatBridge.class),
                        new MockScheduler(),
                        mock(MessageService.class),
                        mockExecutor);

        Javalin mockApp = mock(Javalin.class);
        routes.register(mockApp);

        // Verify scheduleAtFixedRate was called with the 20 000 ms interval from defaultConfig()
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mockExecutor)
                .scheduleAtFixedRate(
                        taskCaptor.capture(),
                        eq(20_000L),
                        eq(20_000L),
                        eq(TimeUnit.MILLISECONDS));

        // Set up one "open" client and one "closed" client.
        // useConstructor initialises ctx.session so that isOpen() returns the stubbed value.
        WsContext openCtx = mockCtxWithSession(true);
        WsContext closedCtx = mockCtxWithSession(false);
        routes.handleConnect(openCtx);
        routes.handleConnect(closedCtx);
        assertThat(routes.connectedClientCount()).isEqualTo(2);

        // Run the captured heartbeat task — closed session should be removed
        taskCaptor.getValue().run();

        assertThat(routes.connectedClientCount()).isEqualTo(1);
        // Open client should also have received a PING (first call = VERIFICATION from connect,
        // second = PING from heartbeat)
        verify(openCtx, atLeastOnce()).send(argThat((String s) -> s.contains("\"type\":\"PING\"")));
    }

    // -------------------------------------------------------------------------
    // Test 10 — shutdown() stops heartbeat executor and unsubscribes from ChatBridge
    // -------------------------------------------------------------------------

    @Test
    void shutdown_stopsHeartbeatExecutor_andUnsubscribesFromChatBridge() {
        ScheduledExecutorService mockExecutor = mock(ScheduledExecutorService.class);
        ChatBridge mockBridge = mock(ChatBridge.class);
        ChatBridge.Subscription mockSub = mock(ChatBridge.Subscription.class);
        when(mockBridge.subscribe(any())).thenReturn(mockSub);

        ChatWebSocketRoutes routes =
                makeRoutesWithExecutor(
                        mockBridge,
                        new MockScheduler(),
                        mock(MessageService.class),
                        mockExecutor);

        Javalin mockApp = mock(Javalin.class);
        routes.register(mockApp); // sets chatSubscription = mockSub

        routes.shutdown();

        verify(mockSub).unsubscribe();
        verify(mockExecutor).shutdown();

        // Second shutdown() must be idempotent — must not throw
        routes.shutdown();
        // Executor shutdown is called once more (it's safe); unsubscribe must NOT be called again
        verify(mockExecutor, times(2)).shutdown();
        verify(mockSub, times(1)).unsubscribe();
    }
}
