package de.kreiscraft.modules.jail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.kreiscraft.core.message.MessageService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class JailListenerTest {

    @TempDir
    Path tmp;

    private ServerMock server;
    private PluginMock plugin;
    private DefaultJailService service;
    private MessageService messages;
    private AtomicReference<JailConfig> configRef;
    private World world;
    private Location jailLocation;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        jailLocation = new Location(world, 126, 91, 92);

        JailData data = new JailData(tmp);
        data.load();
        service = new DefaultJailService(
                data, new de.kreiscraft.core.config.YamlConfigService(tmp, getClass().getClassLoader()));
        service.setJailLocation(jailLocation);
        messages = mock(MessageService.class);

        configRef = new AtomicReference<>(new JailConfig(List.of("msg", "tell")));

        server.getPluginManager()
                .registerEvents(new JailListener(service, configRef, messages), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ── PlayerCommandPreprocessEvent ─────────────────────────────────────────

    @Test
    void onCommand_jailedPlayer_blockedCommand_isCancelled() {
        PlayerMock player = server.addPlayer("Alice");
        service.jail(player.getUniqueId(), player.getLocation(), "test");

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/msg Bob hi");
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
        verify(messages).send(player, "jail.command-blocked");
    }

    @Test
    void onCommand_jailedPlayer_allowedCommand_isNotCancelled() {
        PlayerMock player = server.addPlayer("Alice");
        service.jail(player.getUniqueId(), player.getLocation(), "test");

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/tp 0 64 0");
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
        verify(messages, never()).send(player, "jail.command-blocked");
    }

    @Test
    void onCommand_notJailed_blockedCommand_isNotCancelled() {
        PlayerMock player = server.addPlayer("Alice");

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/msg Bob hi");
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void onCommand_jailedPlayer_jailCommand_alwaysAllowed() {
        PlayerMock player = server.addPlayer("Alice");
        service.jail(player.getUniqueId(), player.getLocation(), "test");
        // Put jail in blocked list to confirm the override works
        configRef.set(new JailConfig(List.of("jail", "msg")));

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/jail Alice");
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void onCommand_jailedPlayer_kerkerCommand_alwaysAllowed() {
        PlayerMock player = server.addPlayer("Alice");
        service.jail(player.getUniqueId(), player.getLocation(), "test");
        configRef.set(new JailConfig(List.of("kerker", "msg")));

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/kerker Alice");
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void onCommand_jailedPlayer_kreiscrafthelpCommand_alwaysAllowed() {
        PlayerMock player = server.addPlayer("Alice");
        service.jail(player.getUniqueId(), player.getLocation(), "test");
        configRef.set(new JailConfig(List.of("kreiscrafthelp", "msg")));

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/kreiscrafthelp");
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void onCommand_namespacedBlockedCommand_stripsNamespace() {
        PlayerMock player = server.addPlayer("Alice");
        service.jail(player.getUniqueId(), player.getLocation(), "test");
        configRef.set(new JailConfig(List.of("msg")));

        PlayerCommandPreprocessEvent event =
                new PlayerCommandPreprocessEvent(player, "/bukkit:msg Bob hi");
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
    }

    // ── PlayerInteractEvent ──────────────────────────────────────────────────

    @Test
    void onInteract_jailedPlayer_isCancelled() {
        PlayerMock player = server.addPlayer("Alice");
        service.jail(player.getUniqueId(), player.getLocation(), "test");

        // Use a real block so isCancelled() starts as false (null block → useBlock=DENY by Bukkit)
        var block = world.getBlockAt(0, 64, 0);
        PlayerInteractEvent event =
                new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void onInteract_notJailed_isNotCancelled() {
        PlayerMock player = server.addPlayer("Alice");

        var block = world.getBlockAt(0, 64, 0);
        PlayerInteractEvent event =
                new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
    }

    // ── PlayerJoinEvent ──────────────────────────────────────────────────────

    @Test
    void onJoin_jailedPlayer_teleportedToJailLocation() {
        PlayerMock player = server.addPlayer("Alice");
        // Jail the player (capture their current location as restore)
        service.jail(player.getUniqueId(), player.getLocation(), "test");

        // Move player away from jail location
        player.teleport(new Location(world, 500, 64, 500));

        // Fire the join event directly (simulates reconnect)
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));

        assertThat(player.getLocation().getBlockX()).isEqualTo(126);
        assertThat(player.getLocation().getBlockY()).isEqualTo(91);
        assertThat(player.getLocation().getBlockZ()).isEqualTo(92);
    }

    @Test
    void onJoin_notJailed_notTeleportedToJailLocation() {
        PlayerMock player = server.addPlayer("Alice");
        Location original = new Location(world, 10, 64, 10);
        player.teleport(original);

        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));

        // Should NOT be moved to jail location
        assertThat(player.getLocation().getBlockX()).isNotEqualTo(126);
    }

    @Test
    void onJoin_usesUpdatedJailLocationFromServerConfiguration() {
        PlayerMock player = server.addPlayer("Alice");
        service.jail(player.getUniqueId(), player.getLocation(), "test");

        // Update the operator-owned jail location in Server Configuration
        Location newJailLocation = new Location(world, 200, 70, 200);
        service.setJailLocation(newJailLocation);

        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));

        assertThat(player.getLocation().getBlockX()).isEqualTo(200);
        assertThat(player.getLocation().getBlockY()).isEqualTo(70);
        assertThat(player.getLocation().getBlockZ()).isEqualTo(200);
    }
}
