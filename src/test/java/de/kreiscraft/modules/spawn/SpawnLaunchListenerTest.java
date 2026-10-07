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
    private World nether;
    private SpawnLaunchListener listener;

    /** Mutable reference updated to simulate config reload. */
    private SpawnConfig config;

    /** The trigger block coordinates used in these tests. */
    private static final double TX = 10.0, TY = 64.0, TZ = -5.0;

    private SpawnConfig makeConfig(double vy) {
        return new SpawnConfig(
                100,
                new SpawnConfig.GlideConfig(true, 3, 30, null),
                new SpawnConfig.SwapHandBoostConfig(false, 3.0),
                new SpawnConfig.SpawnLaunchConfig(
                        true,
                        new Location(world, TX, TY, TZ, 0f, 0f),
                        new Vector(0, vy, 0)),
                new SpawnConfig.GlideBoostConfig(false, 2.0, 5));
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        nether = server.addSimpleWorld("world_nether");

        config = makeConfig(5.0);
        listener = new SpawnLaunchListener(() -> config);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void enterTriggerBlock_appliesVelocity() {
        PlayerMock player = server.addPlayer("alice");

        // Move from outside the trigger to inside it (same world)
        Location from = new Location(world, TX - 1, TY, TZ);
        Location to = new Location(world, TX + 0.5, TY + 0.1, TZ + 0.5);

        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        server.getPluginManager().callEvent(event);

        assertThat(player.getVelocity().getY()).isEqualTo(5.0);
    }

    @Test
    void enterTriggerCoords_inDifferentWorld_doesNotApplyVelocity() {
        // Issue #1: world guard — same coordinates but WRONG world must NOT launch.
        PlayerMock player = server.addPlayer("bob");

        Location from = new Location(nether, TX - 1, TY, TZ);
        Location to = new Location(nether, TX + 0.5, TY + 0.1, TZ + 0.5);
        player.setLocation(from); // ensure player is in the nether

        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        server.getPluginManager().callEvent(event);

        // Velocity must NOT be changed — player is in a different world.
        assertThat(player.getVelocity().getY()).isEqualTo(0.0);
    }

    @Test
    void moveWithinTriggerBlock_doesNotReapplyVelocity() {
        PlayerMock player = server.addPlayer("carol");

        // Both from and to are inside the trigger block → "from" already on trigger
        Location from = new Location(world, TX + 0.1, TY + 0.1, TZ + 0.1);
        Location to = new Location(world, TX + 0.3, TY + 0.1, TZ + 0.3);

        player.setVelocity(new Vector(0, 0, 0));

        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        server.getPluginManager().callEvent(event);

        // Velocity should NOT be changed (already on trigger)
        assertThat(player.getVelocity().getY()).isEqualTo(0.0);
    }

    @Test
    void moveAwayFromTrigger_doesNotApplyVelocity() {
        PlayerMock player = server.addPlayer("diana");

        Location from = new Location(world, TX + 0.5, TY, TZ + 0.5);
        Location to = new Location(world, TX + 2, TY, TZ);

        player.setVelocity(new Vector(0, 0, 0));

        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        server.getPluginManager().callEvent(event);

        assertThat(player.getVelocity().getY()).isEqualTo(0.0);
    }

    @Test
    void moveNotNearTrigger_doesNothing() {
        PlayerMock player = server.addPlayer("eve");

        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 1, 64, 0);
        player.setVelocity(new Vector(0, 0, 0));

        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        server.getPluginManager().callEvent(event);

        assertThat(player.getVelocity().getY()).isEqualTo(0.0);
    }

    @Test
    void configReload_newVelocity_isHonored() {
        // Simulate config reload: change launch velocity Y from 5 to 10
        config = makeConfig(10.0);

        PlayerMock player = server.addPlayer("frank");
        Location from = new Location(world, TX - 1, TY, TZ);
        Location to = new Location(world, TX + 0.5, TY + 0.1, TZ + 0.5);

        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        server.getPluginManager().callEvent(event);

        assertThat(player.getVelocity().getY()).isEqualTo(10.0);
    }
}
