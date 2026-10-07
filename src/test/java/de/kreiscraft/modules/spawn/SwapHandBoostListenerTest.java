package de.kreiscraft.modules.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class SwapHandBoostListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private World world;
    private SwapHandBoostListener listener;

    /** Mutable reference updated to simulate config reload. */
    private SpawnConfig config;

    private SpawnConfig makeConfig(double multiplier) {
        return new SpawnConfig(
                50,
                new SpawnConfig.GlideConfig(true, 3),
                new SpawnConfig.SwapHandBoostConfig(true, multiplier),
                new SpawnConfig.GlideBoostConfig(false, 2.0, 5));
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");

        config = makeConfig(3.0);
        listener = new SwapHandBoostListener(() -> config);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void swapHand_whileGliding_cancelsEventAndAppliesVelocity() {
        PlayerMock player = server.addPlayer("alice");
        player.setLocation(new Location(world, 0, 64, 0, 0f, 0f));
        player.setGliding(true);

        PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(
                player, player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand());
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(player.getVelocity().length()).isGreaterThan(0.0);
    }

    @Test
    void swapHand_notGliding_doesNothing() {
        PlayerMock player = server.addPlayer("bob");
        player.setLocation(new Location(world, 0, 64, 0));
        // player is NOT gliding (default)

        Vector beforeVelocity = player.getVelocity().clone();

        PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(
                player, player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand());
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(player.getVelocity()).isEqualTo(beforeVelocity);
    }

    @Test
    void swapHand_velocityMagnitude_matchesMultiplier() {
        PlayerMock player = server.addPlayer("carol");
        player.setLocation(new Location(world, 0, 64, 0, 0f, 0f));
        player.setGliding(true);

        PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(
                player, player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand());
        server.getPluginManager().callEvent(event);

        Vector vel = player.getVelocity();
        assertThat(vel.length()).isCloseTo(3.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void configReload_newMultiplier_isHonored() {
        // First swap with multiplier 3.0
        PlayerMock player = server.addPlayer("diana");
        player.setLocation(new Location(world, 0, 64, 0, 0f, 0f));
        player.setGliding(true);

        PlayerSwapHandItemsEvent first = new PlayerSwapHandItemsEvent(
                player, player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand());
        server.getPluginManager().callEvent(first);
        double firstMag = player.getVelocity().length();
        assertThat(firstMag).isCloseTo(3.0, org.assertj.core.data.Offset.offset(0.01));

        // Simulate config reload: change multiplier to 6.0
        config = makeConfig(6.0);

        // Un-glide and re-glide to fire a fresh event
        player.setGliding(false);
        player.setGliding(true);
        PlayerSwapHandItemsEvent second = new PlayerSwapHandItemsEvent(
                player, player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand());
        server.getPluginManager().callEvent(second);
        double secondMag = player.getVelocity().length();
        assertThat(secondMag).isCloseTo(6.0, org.assertj.core.data.Offset.offset(0.01));
    }
}
