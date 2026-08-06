package de.kreiscraft.modules.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.testsupport.MockScheduler;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class GlideListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private World world;
    private DefaultSpawnService service;
    private SpawnConfig.GlideConfig glideConfig;
    private MockScheduler scheduler;
    private GlideListener listener;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");

        SpawnData data = new SpawnData(tmp);
        data.load();
        SpawnConfig config = new SpawnConfig(100, null, null, null);
        service = new DefaultSpawnService(data, config);

        // Set spawn at origin in the world
        service.setSpawn(new Location(world, 0, 64, 0));

        glideConfig = new SpawnConfig.GlideConfig(true, 3, 30, new LocationSpec("", 0, 0, 0, 0, 0));
        scheduler = new MockScheduler();
        listener = new GlideListener(service, glideConfig, scheduler, server);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        listener.shutdown();
        MockBukkit.unmock();
    }

    @Test
    void tickTask_grantsAllowFlight_toEligibleSurvivalPlayer() {
        PlayerMock player = server.addPlayer("alice");
        player.setGameMode(GameMode.SURVIVAL);
        // Location is at origin — within spawn radius of 30
        player.setLocation(new Location(world, 5, 64, 5));

        // Advance 3 ticks (150ms) to fire the repeating task
        scheduler.advance(Duration.ofMillis(150));

        assertThat(player.getAllowFlight()).isTrue();
    }

    @Test
    void tickTask_revokesAllowFlight_outsideRadius() {
        PlayerMock player = server.addPlayer("bob");
        player.setGameMode(GameMode.SURVIVAL);
        // Move player far outside spawn radius
        player.setLocation(new Location(world, 100, 64, 100));

        scheduler.advance(Duration.ofMillis(150));

        assertThat(player.getAllowFlight()).isFalse();
    }

    @Test
    void tickTask_doesNotGrantFlight_toCreativePlayer() {
        PlayerMock player = server.addPlayer("charlie");
        player.setGameMode(GameMode.CREATIVE);
        player.setLocation(new Location(world, 5, 64, 5));

        scheduler.advance(Duration.ofMillis(150));

        // Creative players are not granted flight by this listener
        // (they already have it by default; the listener skips non-survival)
        assertThat(listener.isFlying(player.getUniqueId())).isFalse();
    }

    @Test
    void toggleFlight_whenEligible_startsGlide() {
        PlayerMock player = server.addPlayer("diana");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5));
        player.setAllowFlight(true);

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(listener.isFlying(player.getUniqueId())).isTrue();
    }

    @Test
    void toggleFlight_whenNotEligible_doesNotInterfere() {
        PlayerMock player = server.addPlayer("eve");
        player.setGameMode(GameMode.SURVIVAL);
        // Far from spawn
        player.setLocation(new Location(world, 200, 64, 200));

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(listener.isFlying(player.getUniqueId())).isFalse();
    }

    @Test
    void entityDamage_fallWhileGliding_cancelled() {
        PlayerMock player = server.addPlayer("frank");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5));
        player.setAllowFlight(true);

        // Mark player as flying via the toggle event
        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);
        assertThat(listener.isFlying(player.getUniqueId())).isTrue();

        // Now fire FALL damage — should be cancelled
        EntityDamageEvent dmg = new EntityDamageEvent(player,
                EntityDamageEvent.DamageCause.FALL, 5.0);
        server.getPluginManager().callEvent(dmg);

        assertThat(dmg.isCancelled()).isTrue();
    }

    @Test
    void entityDamage_flyIntoWallWhileGliding_cancelled() {
        PlayerMock player = server.addPlayer("grace");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5));
        player.setAllowFlight(true);

        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);

        EntityDamageEvent dmg = new EntityDamageEvent(player,
                EntityDamageEvent.DamageCause.FLY_INTO_WALL, 3.0);
        server.getPluginManager().callEvent(dmg);

        assertThat(dmg.isCancelled()).isTrue();
    }

    @Test
    void entityDamage_otherCauseWhileGliding_notCancelled() {
        PlayerMock player = server.addPlayer("henry");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5));
        player.setAllowFlight(true);

        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);

        // FIRE damage while gliding → must NOT be cancelled
        EntityDamageEvent dmg = new EntityDamageEvent(player,
                EntityDamageEvent.DamageCause.FIRE, 4.0);
        server.getPluginManager().callEvent(dmg);

        assertThat(dmg.isCancelled()).isFalse();
    }

    @Test
    void entityDamage_fallWhenNotGliding_notCancelled() {
        PlayerMock player = server.addPlayer("ivy");
        // Not in flying set

        EntityDamageEvent dmg = new EntityDamageEvent(player,
                EntityDamageEvent.DamageCause.FALL, 5.0);
        server.getPluginManager().callEvent(dmg);

        assertThat(dmg.isCancelled()).isFalse();
    }
}
