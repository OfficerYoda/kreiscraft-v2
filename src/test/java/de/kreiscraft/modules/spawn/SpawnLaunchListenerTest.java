package de.kreiscraft.modules.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class SpawnLaunchListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private World world;
    private SpawnLaunchListener listener;

    /** The trigger block coordinates used in these tests. */
    private static final double TX = 10.0, TY = 64.0, TZ = -5.0;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");

        SpawnConfig.SpawnLaunchConfig launchConfig = new SpawnConfig.SpawnLaunchConfig(
                true,
                new LocationSpec("world", TX, TY, TZ, 0, 0),
                new VelocityRecord(0, 5, 0));
        listener = new SpawnLaunchListener(launchConfig);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void enterTriggerBlock_appliesVelocity() {
        PlayerMock player = server.addPlayer("alice");

        // Move from outside the trigger to inside it
        Location from = new Location(world, TX - 1, TY, TZ);
        Location to = new Location(world, TX + 0.5, TY + 0.1, TZ + 0.5); // same block as trigger

        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        server.getPluginManager().callEvent(event);

        assertThat(player.getVelocity().getY()).isEqualTo(5.0);
    }

    @Test
    void moveWithinTriggerBlock_doesNotReapplyVelocity() {
        PlayerMock player = server.addPlayer("bob");

        // Both from and to are inside the trigger block → "from" already on trigger
        Location from = new Location(world, TX + 0.1, TY + 0.1, TZ + 0.1);
        Location to = new Location(world, TX + 0.3, TY + 0.1, TZ + 0.3);

        // Reset velocity
        player.setVelocity(new Vector(0, 0, 0));

        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        server.getPluginManager().callEvent(event);

        // Velocity should NOT be changed (already on trigger)
        assertThat(player.getVelocity().getY()).isEqualTo(0.0);
    }

    @Test
    void moveAwayFromTrigger_doesNotApplyVelocity() {
        PlayerMock player = server.addPlayer("carol");

        // Move from trigger to outside
        Location from = new Location(world, TX + 0.5, TY, TZ + 0.5);
        Location to = new Location(world, TX + 2, TY, TZ);

        player.setVelocity(new Vector(0, 0, 0));

        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        server.getPluginManager().callEvent(event);

        assertThat(player.getVelocity().getY()).isEqualTo(0.0);
    }

    @Test
    void moveNotNearTrigger_doesNothing() {
        PlayerMock player = server.addPlayer("diana");

        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 1, 64, 0);
        player.setVelocity(new Vector(0, 0, 0));

        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        server.getPluginManager().callEvent(event);

        assertThat(player.getVelocity().getY()).isEqualTo(0.0);
    }
}
