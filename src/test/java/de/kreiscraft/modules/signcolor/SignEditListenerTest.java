package de.kreiscraft.modules.signcolor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
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

        SignEditListener listener = new SignEditListener();
        listener.onSignChange(event);

        // Capture the component set for line 0 and verify it is MiniMessage-parsed
        ArgumentCaptor<Component> cap0 = ArgumentCaptor.forClass(Component.class);
        verify(event).line(org.mockito.Mockito.eq(0), cap0.capture());
        String plain0 =
                PlainTextComponentSerializer.plainText().serialize(cap0.getValue());
        assertThat(plain0).isEqualTo("Hello");

        // Line 0 via MiniMessage round-trip should equal direct MiniMessage parse
        Component expected0 =
                MiniMessage.miniMessage().deserialize("<red>Hello");
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
    void onSignChange_withoutPermission_doesNotModifyLines() {
        PlayerMock player = server.addPlayer("bob");
        // bob has no kreiscraft.signcolor.use permission

        SignChangeEvent event = mock(SignChangeEvent.class);
        when(event.getPlayer()).thenReturn(player);

        SignEditListener listener = new SignEditListener();
        listener.onSignChange(event);

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

        SignEditListener listener = new SignEditListener();
        listener.onSignChange(event);

        // No line should be set when all lines are null
        verify(event, never()).line(org.mockito.Mockito.anyInt(), org.mockito.Mockito.any());
    }
}
