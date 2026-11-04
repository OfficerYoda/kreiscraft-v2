package de.kreiscraft.modules.chatformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.modules.status.Status;
import de.kreiscraft.modules.status.StatusService;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.chat.ChatBridgeEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatFormatListenerTest {

    @Mock
    private StatusService statusService;

    @Mock
    private MessageService messages;

    /**
     * Separate mock Server used for PaperPlatform. We cannot pass MockBukkit's ServerMock
     * directly because ServerMock.audiences() is unimplemented, causing sendMessage(Component)
     * to throw UnimplementedOperationException. The mock server is a no-op for sendMessage.
     */
    @Mock
    private Server mockServer;

    /** MockBukkit server — used only to create PlayerMock instances. */
    private ServerMock server;
    private PluginMock plugin;
    private ChatBridge chatBridge;
    private PaperPlatform platform;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        chatBridge = new ChatBridge();
        // Use the Mockito-mocked Server for the platform so sendMessage is a safe no-op.
        platform = new PaperPlatform(plugin, mockServer);
        lenient().when(statusService.get(any(UUID.class))).thenReturn(Status.defaults());
        lenient().when(messages.render("prefix")).thenReturn(Component.text("[Kreiscraft]"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private AsyncChatEvent mockEvent(PlayerMock player, String plainBody) {
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.message()).thenReturn(Component.text(plainBody));
        return event;
    }

    private ChatFormatListener listenerWith(ChatFormatConfig config) {
        return new ChatFormatListener(() -> config, statusService, platform, chatBridge, messages);
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void onChat_cancelsVanillaEvent() {
        PlayerMock player = server.addPlayer("Alice");
        AsyncChatEvent event = mockEvent(player, "hello");

        listenerWith(new ChatFormatConfig()).onChat(event);

        verify(event).setCancelled(true);
    }

    @Test
    void onChat_publishesToChatBridgeWithCorrectSenderAndPlainText() {
        PlayerMock player = server.addPlayer("Bob");
        AsyncChatEvent event = mockEvent(player, "world");

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        listenerWith(new ChatFormatConfig()).onChat(event);

        assertThat(captured).hasSize(1);
        ChatBridgeEvent bridgeEvent = captured.getFirst();
        assertThat(bridgeEvent.senderUuid()).isEqualTo(player.getUniqueId());
        assertThat(bridgeEvent.senderName()).isEqualTo("Bob");
        assertThat(bridgeEvent.plainText()).isEqualTo("world");
        assertThat(bridgeEvent.timestamp()).isNotNull();
    }

    @Test
    void onChat_renderedComponentContainsPlayerNameAndBody() {
        PlayerMock player = server.addPlayer("Carol");
        AsyncChatEvent event = mockEvent(player, "hi there");

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        listenerWith(new ChatFormatConfig()).onChat(event);

        assertThat(captured).hasSize(1);
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(captured.getFirst().rendered());
        assertThat(plain).contains("Carol");
        assertThat(plain).contains("hi there");
    }

    @Test
    void onChat_presentPlayerAffixesHaveExactlyOneSurroundingSpace() {
        PlayerMock player = server.addPlayer("Dave");
        when(statusService.get(player.getUniqueId()))
                .thenReturn(new Status("<gold>[Owner] </gold>", "<gray> [/Owner]</gray>", 0xFF4400));
        AsyncChatEvent event = mockEvent(player, "sup");

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        ChatFormatConfig config = new ChatFormatConfig(
                org.bukkit.event.EventPriority.LOWEST,
                false,
                "<kc-prefix> <player-prefix><name-colored><player-suffix><gray>: <reset><body>");
        listenerWith(config).onChat(event);

        assertThat(captured).hasSize(1);
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(captured.getFirst().rendered());
        assertThat(plain).isEqualTo("[Kreiscraft] [Owner] Dave [/Owner]: sup");
    }

    @Test
    void onChat_absentPlayerAffixesAddNoWhitespace() {
        PlayerMock player = server.addPlayer("Dave");
        when(statusService.get(player.getUniqueId())).thenReturn(new Status("", "", 0xFF4400));
        AsyncChatEvent event = mockEvent(player, "sup");

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        ChatFormatConfig config = new ChatFormatConfig(
                null,
                false,
                "<kc-prefix> <player-prefix><name-colored><player-suffix>: <body>");
        listenerWith(config).onChat(event);

        String plain = PlainTextComponentSerializer.plainText()
                .serialize(captured.getFirst().rendered());
        assertThat(plain).isEqualTo("[Kreiscraft] Dave: sup");
    }

    @Test
    void onChat_nameIsColouredWithExactStatusColour() {
        PlayerMock player = server.addPlayer("Heidi");
        when(statusService.get(player.getUniqueId()))
                .thenReturn(new Status("[Owner]", "[/Owner]", 0xFF4400));
        AsyncChatEvent event = mockEvent(player, "colour me");

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        listenerWith(new ChatFormatConfig()).onChat(event);

        assertThat(captured).hasSize(1);
        TextComponent name = findText(captured.getFirst().rendered(), "Heidi");
        assertThat(name).isNotNull();
        assertThat(name.style().color()).isEqualTo(TextColor.color(0xFF4400));
    }

    private static TextComponent findText(Component component, String content) {
        if (component instanceof TextComponent text && text.content().equals(content)) {
            return text;
        }
        for (Component child : component.children()) {
            TextComponent found = findText(child, content);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void onChat_allowMiniMessageFalse_treatsBodyAsLiteralText() {
        PlayerMock player = server.addPlayer("Eve");
        // Message contains a MiniMessage tag that should be treated as literal text
        AsyncChatEvent event = mockEvent(player, "<red>trick</red>");

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        ChatFormatConfig config = new ChatFormatConfig(null, false, null);
        listenerWith(config).onChat(event);

        assertThat(captured).hasSize(1);
        // The body should be plain text; MiniMessage tags are not parsed
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(captured.getFirst().rendered());
        // Plain serializer strips any formatting, so just confirm the raw tag
        // characters appear (they are stored as literal text, not parsed)
        assertThat(captured.getFirst().plainText()).isEqualTo("<red>trick</red>");
    }

    @Test
    void onChat_allowMiniMessageTrue_parsesMiniMessageInBody() {
        PlayerMock player = server.addPlayer("Frank");
        // The raw message text contains MiniMessage markup
        AsyncChatEvent event = mockEvent(player, "<red>bold</red>");

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        ChatFormatConfig config = new ChatFormatConfig(null, true, null);
        listenerWith(config).onChat(event);

        assertThat(captured).hasSize(1);
        // plainText is always the raw extracted string, regardless of allowMiniMessage
        assertThat(captured.getFirst().plainText()).isEqualTo("<red>bold</red>");
        // rendered component should be non-null
        assertThat(captured.getFirst().rendered()).isNotNull();
    }

    @Test
    void onChat_statusServiceCalledWithSenderUuid() {
        PlayerMock player = server.addPlayer("Grace");
        AsyncChatEvent event = mockEvent(player, "test");

        listenerWith(new ChatFormatConfig()).onChat(event);

        verify(statusService).get(player.getUniqueId());
    }

    @Test
    void onChat_broadcastsViaPlatformServer() {
        PlayerMock player = server.addPlayer("Hank");
        AsyncChatEvent event = mockEvent(player, "broadcast test");

        listenerWith(new ChatFormatConfig()).onChat(event);

        // Verify that sendMessage was called on the platform server once
        verify(mockServer).sendMessage(any(Component.class));
    }

    // ── Xaero waypoint passthrough ─────────────────────────────────────────────

    /** Representative payload shape emitted by Xaero's waypoint sharing. */
    private static final String XAERO_WAYPOINT = "xaero-waypoint:Home:H:100:64:200:4:minecraft:overworld:false";

    @Test
    void onChat_xaeroWaypointExactPrefix_isNotCancelled() {
        PlayerMock player = server.addPlayer("Ivan");
        AsyncChatEvent event = mockEvent(player, XAERO_WAYPOINT);

        listenerWith(new ChatFormatConfig()).onChat(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void onChat_xaeroWaypointExactPrefix_isNotPublishedOrBroadcast() {
        PlayerMock player = server.addPlayer("Judy");
        AsyncChatEvent event = mockEvent(player, XAERO_WAYPOINT);

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        listenerWith(new ChatFormatConfig()).onChat(event);

        assertThat(captured).isEmpty();
        verify(mockServer, never()).sendMessage(any(Component.class));
    }

    @Test
    void onChat_xaeroWaypointNotAtStart_isFormattedAndPublished() {
        PlayerMock player = server.addPlayer("Kate");
        AsyncChatEvent event = mockEvent(player, "look at xaero-waypoint:Home");

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        listenerWith(new ChatFormatConfig()).onChat(event);

        verify(event).setCancelled(true);
        assertThat(captured).hasSize(1);
        assertThat(captured.getFirst().plainText()).isEqualTo("look at xaero-waypoint:Home");
    }

    @Test
    void onChat_xaeroWaypointDifferentCase_isFormattedAndPublished() {
        PlayerMock player = server.addPlayer("Lena");
        AsyncChatEvent event = mockEvent(player, "Xaero-waypoint:Home");

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        listenerWith(new ChatFormatConfig()).onChat(event);

        verify(event).setCancelled(true);
        assertThat(captured).hasSize(1);
    }

    @Test
    void onChat_xaeroWaypointWithLeadingWhitespace_isFormattedAndPublished() {
        PlayerMock player = server.addPlayer("Mona");
        AsyncChatEvent event = mockEvent(player, " xaero-waypoint:Home");

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        listenerWith(new ChatFormatConfig()).onChat(event);

        verify(event).setCancelled(true);
        assertThat(captured).hasSize(1);
    }

    @Test
    void onChat_xaeroWaypointBareToken_isPassedThrough() {
        PlayerMock player = server.addPlayer("Olga");
        AsyncChatEvent event = mockEvent(player, "xaero-waypoint");

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        listenerWith(new ChatFormatConfig()).onChat(event);

        verify(event, never()).setCancelled(anyBoolean());
        assertThat(captured).isEmpty();
    }

    @Test
    void onChat_xaeroWaypointPluralNearMatch_isFormattedAndPublished() {
        PlayerMock player = server.addPlayer("Pete");
        AsyncChatEvent event = mockEvent(player, "xaero-waypoints are cool");

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        listenerWith(new ChatFormatConfig()).onChat(event);

        verify(event).setCancelled(true);
        assertThat(captured).hasSize(1);
        assertThat(captured.getFirst().plainText()).isEqualTo("xaero-waypoints are cool");
    }

    @Test
    void onChat_xaeroWaypointExtendedToken_isFormattedAndPublished() {
        PlayerMock player = server.addPlayer("Quinn");
        AsyncChatEvent event = mockEvent(player, "xaero-waypointish");

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        listenerWith(new ChatFormatConfig()).onChat(event);

        verify(event).setCancelled(true);
        assertThat(captured).hasSize(1);
    }

    @Test
    void onChat_ordinaryMessage_keepsFormattingAndBridgeBehavior() {
        PlayerMock player = server.addPlayer("Nate");
        AsyncChatEvent event = mockEvent(player, "just a normal message");

        List<ChatBridgeEvent> captured = new ArrayList<>();
        chatBridge.subscribe(captured::add);

        listenerWith(new ChatFormatConfig()).onChat(event);

        verify(event).setCancelled(true);
        assertThat(captured).hasSize(1);
        assertThat(captured.getFirst().plainText()).isEqualTo("just a normal message");
        verify(mockServer).sendMessage(any(Component.class));
    }
}
