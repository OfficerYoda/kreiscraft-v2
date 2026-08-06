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

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");

        SpawnConfig.SwapHandBoostConfig boostConfig =
                new SpawnConfig.SwapHandBoostConfig(true, 3.0);
        listener = new SwapHandBoostListener(boostConfig);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void swapHand_whileGliding_cancelsEventAndAppliesVelocity() {
        PlayerMock player = server.addPlayer("alice");
        player.setLocation(new Location(world, 0, 64, 0, 0f, 0f)); // facing +z direction
        player.setGliding(true);

        PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(
                player, player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand());
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
        // Velocity should be set (direction × 3.0 multiplier)
        assertThat(player.getVelocity()).isNotNull();
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
        // velocity should not have been changed by this listener
        assertThat(player.getVelocity()).isEqualTo(beforeVelocity);
    }

    @Test
    void swapHand_velocityMagnitude_matchesMultiplier() {
        PlayerMock player = server.addPlayer("carol");
        // Place player looking forward (yaw=0, pitch=0 → direction is (0, 0, 1) roughly)
        player.setLocation(new Location(world, 0, 64, 0, 0f, 0f));
        player.setGliding(true);

        PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(
                player, player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand());
        server.getPluginManager().callEvent(event);

        // The velocity magnitude should roughly equal 3.0 (the vectorMultiplier)
        Vector vel = player.getVelocity();
        assertThat(vel.length()).isCloseTo(3.0, org.assertj.core.data.Offset.offset(0.01));
    }
}
