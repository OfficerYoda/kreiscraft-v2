package de.kreiscraft.modules.signcolor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.PlaceholderResolver;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.sign.Side;
import org.bukkit.event.block.SignChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockito.ArgumentCaptor;

class SignEditListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private LegacyTextFormatter formatter;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        formatter =
                new LegacyTextFormatter(
                        new PlaceholderResolver(server, ZoneId.of("UTC"), Clock.systemUTC()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private SignEditListener newListener() {
        return new SignEditListener(formatter);
    }

    @Test
    void onSignChange_withPermission_translatesMiniMessageOnAllLines() {
        PlayerMock player = server.addPlayer("alice");
        player.addAttachment(plugin, "kreiscraft.signcolor.use", true);

        SignChangeEvent event = mock(SignChangeEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.line(0)).thenReturn(Component.text("<red>Hello"));
        when(event.line(1)).thenReturn(Component.text("<green>World"));
        when(event.line(2)).thenReturn(Component.text("plain"));
        when(event.line(3)).thenReturn(Component.text(""));

        newListener().onSignChange(event);

        // Capture the component set for line 0 and verify it is MiniMessage-parsed
        ArgumentCaptor<Component> cap0 = ArgumentCaptor.forClass(Component.class);
        verify(event).line(org.mockito.Mockito.eq(0), cap0.capture());
        String plain0 =
                PlainTextComponentSerializer.plainText().serialize(cap0.getValue());
        assertThat(plain0).isEqualTo("Hello");

        // Line 0 via MiniMessage round-trip should equal direct MiniMessage parse
        Component expected0 = MiniMessage.miniMessage().deserialize("<red>Hello");
        assertThat(cap0.getValue()).isEqualTo(expected0);

        // Line 2 - plain text passes through unchanged
        ArgumentCaptor<Component> cap2 = ArgumentCaptor.forClass(Component.class);
        verify(event).line(org.mockito.Mockito.eq(2), cap2.capture());
        String plain2 =
                PlainTextComponentSerializer.plainText().serialize(cap2.getValue());
        assertThat(plain2).isEqualTo("plain");

        // All 4 lines should have been set
        verify(event).line(org.mockito.Mockito.eq(1), org.mockito.Mockito.any());
        verify(event).line(org.mockito.Mockito.eq(3), org.mockito.Mockito.any());
    }

    @Test
    void onSignChange_translatesLegacyAmpersandColoursAndDecorations() {
        PlayerMock player = server.addPlayer("alice");
        player.addAttachment(plugin, "kreiscraft.signcolor.use", true);

        SignChangeEvent event = mock(SignChangeEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.line(0)).thenReturn(Component.text("&cRed"));
        when(event.line(1)).thenReturn(Component.text("&lBold"));
        when(event.line(2)).thenReturn(Component.text("&6&lGold Bold"));
        when(event.line(3)).thenReturn(Component.text("&rReset"));

        newListener().onSignChange(event);

        assertLine(event, 0, LegacyComponentSerializer.legacyAmpersand().deserialize("&cRed"));
        assertLine(event, 1, LegacyComponentSerializer.legacyAmpersand().deserialize("&lBold"));
        assertLine(
                event, 2, LegacyComponentSerializer.legacyAmpersand().deserialize("&6&lGold Bold"));
        assertLine(event, 3, LegacyComponentSerializer.legacyAmpersand().deserialize("&rReset"));
    }

    @Test
    void onSignChange_resolvesEstablishedKreiscraftPlaceholders() {
        PlayerMock player = server.addPlayer("alice");
        player.addAttachment(plugin, "kreiscraft.signcolor.use", true);

        SignChangeEvent event = mock(SignChangeEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.line(0)).thenReturn(Component.text("Hi %player_name%"));
        when(event.line(1)).thenReturn(Component.text("Players: %server_players%"));
        when(event.line(2)).thenReturn(null);
        when(event.line(3)).thenReturn(null);

        newListener().onSignChange(event);

        assertLine(event, 0, Component.text("Hi alice"));
        assertLine(event, 1, Component.text("Players: 1"));
    }

    @Test
    void onSignChange_blankLine_isFormattedSafely() {
        PlayerMock player = server.addPlayer("alice");
        player.addAttachment(plugin, "kreiscraft.signcolor.use", true);

        SignChangeEvent event = mock(SignChangeEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.line(0)).thenReturn(Component.text("   "));
        when(event.line(1)).thenReturn(null);
        when(event.line(2)).thenReturn(null);
        when(event.line(3)).thenReturn(null);

        newListener().onSignChange(event);

        assertLine(event, 0, Component.text("   "));
    }

    @Test
    void onSignChange_disallowedMiniMessageTag_isRenderedLiterally() {
        PlayerMock player = server.addPlayer("alice");
        player.addAttachment(plugin, "kreiscraft.signcolor.use", true);

        String raw = "<click:open_url:'https://evil.example'>Hi";
        SignChangeEvent event = mock(SignChangeEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.line(0)).thenReturn(Component.text(raw));
        when(event.line(1)).thenReturn(null);
        when(event.line(2)).thenReturn(null);
        when(event.line(3)).thenReturn(null);

        newListener().onSignChange(event);

        ArgumentCaptor<Component> cap = ArgumentCaptor.forClass(Component.class);
        verify(event).line(org.mockito.Mockito.eq(0), cap.capture());
        assertThat(cap.getValue().clickEvent()).isNull();
        assertThat(cap.getValue().hoverEvent()).isNull();
        assertThat(PlainTextComponentSerializer.plainText().serialize(cap.getValue()))
                .isEqualTo(raw);
    }

    @Test
    void onSignChange_withoutPermission_doesNotModifyLines() {
        PlayerMock player = server.addPlayer("bob");
        // bob has no kreiscraft.signcolor.use permission

        SignChangeEvent event = mock(SignChangeEvent.class);
        when(event.getPlayer()).thenReturn(player);

        newListener().onSignChange(event);

        verify(event, never()).line(org.mockito.Mockito.anyInt(), org.mockito.Mockito.any());
    }

    @Test
    void onSignChange_nullLine_isSkipped() {
        PlayerMock player = server.addPlayer("carol");
        player.addAttachment(plugin, "kreiscraft.signcolor.use", true);

        SignChangeEvent event = mock(SignChangeEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.line(0)).thenReturn(null);
        when(event.line(1)).thenReturn(null);
        when(event.line(2)).thenReturn(null);
        when(event.line(3)).thenReturn(null);

        newListener().onSignChange(event);

        // No line should be set when all lines are null
        verify(event, never()).line(org.mockito.Mockito.anyInt(), org.mockito.Mockito.any());
    }

    @Test
    void onSignChange_cancelledEvent_isNotProcessed() {
        PlayerMock player = server.addPlayer("dave");
        player.addAttachment(plugin, "kreiscraft.signcolor.use", true);

        List<Component> lines =
                new ArrayList<>(
                        List.of(
                                Component.text("&cRed"),
                                Component.text("line1"),
                                Component.text("line2"),
                                Component.text("line3")));
        SignChangeEvent event = new SignChangeEvent(mock(Block.class), player, lines, Side.FRONT);
        event.setCancelled(true);
        server.getPluginManager().registerEvents(newListener(), plugin);

        server.getPluginManager().callEvent(event);

        assertThat(event.line(0)).isEqualTo(Component.text("&cRed"));
    }

    private static void assertLine(SignChangeEvent event, int index, Component expected) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(event).line(org.mockito.Mockito.eq(index), captor.capture());
        assertThat(captor.getValue()).isEqualTo(expected);
    }
}
