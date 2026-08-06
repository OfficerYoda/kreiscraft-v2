package de.kreiscraft.modules.pluginhider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class PluginHiderListenerTest {

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
    void onCommandSend_filtersDisallowedCommands() {
        PluginHiderService service = mock(PluginHiderService.class);
        when(service.isAllowed("spawn")).thenReturn(true);
        when(service.isAllowed("ban")).thenReturn(false);
        when(service.isAllowed("help")).thenReturn(false);

        PluginHiderListener listener = new PluginHiderListener(service);
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = server.addPlayer("Alice");
        // ensure player does NOT have bypass permission
        player.addAttachment(plugin, "kreiscraft.pluginhider.bypass", false);

        Set<String> commands = new HashSet<>(Set.of("spawn", "ban", "help", "give"));
        PlayerCommandSendEvent event = new PlayerCommandSendEvent(player, commands);
        server.getPluginManager().callEvent(event);

        // spawn is allowed, help is always preserved, ban and give are filtered
        assertThat(event.getCommands()).contains("spawn", "help");
        assertThat(event.getCommands()).doesNotContain("ban", "give");
    }

    @Test
    void onCommandSend_preservesAllCommandsForBypassPlayer() {
        PluginHiderService service = mock(PluginHiderService.class);
        when(service.isAllowed("ban")).thenReturn(false);

        PluginHiderListener listener = new PluginHiderListener(service);
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = server.addPlayer("Bob");
        player.addAttachment(plugin, "kreiscraft.pluginhider.bypass", true);

        Set<String> commands = new HashSet<>(Set.of("spawn", "ban", "give"));
        PlayerCommandSendEvent event = new PlayerCommandSendEvent(player, commands);
        server.getPluginManager().callEvent(event);

        // bypass player: all commands preserved
        assertThat(event.getCommands()).containsExactlyInAnyOrder("spawn", "ban", "give");
    }

    @Test
    void onCommandSend_helpAlwaysPreserved_evenIfNotInAllowlist() {
        PluginHiderService service = mock(PluginHiderService.class);
        when(service.isAllowed("help")).thenReturn(false);

        PluginHiderListener listener = new PluginHiderListener(service);
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = server.addPlayer("Carol");
        player.addAttachment(plugin, "kreiscraft.pluginhider.bypass", false);

        Set<String> commands = new HashSet<>(Set.of("help", "ban"));
        PlayerCommandSendEvent event = new PlayerCommandSendEvent(player, commands);
        server.getPluginManager().callEvent(event);

        assertThat(event.getCommands()).contains("help");
        assertThat(event.getCommands()).doesNotContain("ban");
    }
}
