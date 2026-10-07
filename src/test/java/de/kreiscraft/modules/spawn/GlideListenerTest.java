package de.kreiscraft.modules.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.testsupport.MockScheduler;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
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
    private MockScheduler scheduler;
    private GlideListener listener;

    /** Mutable config reference — listeners read fresh values on every event. */
    private SpawnConfig config;

    private SpawnConfig makeConfig(int spawnRadius, int multiplyValue) {
        return makeConfigWithBoost(spawnRadius, multiplyValue, false, 2.0, 5);
    }

    private SpawnConfig makeConfigWithBoost(int spawnRadius, int multiplyValue,
            boolean boostEnabled, double boostStrength, int cooldownSeconds) {
        return new SpawnConfig(
                100,
                new SpawnConfig.GlideConfig(true, multiplyValue, spawnRadius, null),
                new SpawnConfig.SwapHandBoostConfig(false, 3.0),
                new SpawnConfig.SpawnLaunchConfig(false, null,
                        new Vector(0, 5, 0)),
                new SpawnConfig.GlideBoostConfig(boostEnabled, boostStrength, cooldownSeconds));
    }

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");

        SpawnData data = new SpawnData(tmp);
        data.load();
        config = makeConfig(30, 3);
        service = new DefaultSpawnService(data, config);

        // Set spawn at origin in the world
        service.setSpawn(new Location(world, 0, 64, 0));

        scheduler = new MockScheduler();
        listener = new GlideListener(service, () -> config, scheduler, server);
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
    void tickTask_revokesAllowFlight_outsideRadius_forSurvivalPlayer() {
        PlayerMock player = server.addPlayer("bob");
        player.setGameMode(GameMode.SURVIVAL);
        // Move player far outside spawn radius
        player.setLocation(new Location(world, 100, 64, 100));

        scheduler.advance(Duration.ofMillis(150));

        assertThat(player.getAllowFlight()).isFalse();
    }

    @Test
    void tickTask_doesNotRevokeAllowFlight_forCreativePlayer() {
        PlayerMock player = server.addPlayer("charlie");
        player.setGameMode(GameMode.CREATIVE);
        // Creative player outside spawn radius — should NOT have flight revoked
        player.setLocation(new Location(world, 100, 64, 100));
        player.setAllowFlight(true); // creative normally has this

        scheduler.advance(Duration.ofMillis(150));

        // Listener must not touch creative player's flight state
        assertThat(player.getAllowFlight()).isTrue();
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
    void toggleFlight_appliesMultiplyValueBoost() {
        // Config has multiplyValue = 3
        PlayerMock player = server.addPlayer("eve");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5, 0f, 0f)); // looking along +z
        player.setAllowFlight(true);

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(event);

        // Velocity should have been set (direction × multiplyValue)
        assertThat(player.getVelocity().length()).isGreaterThan(0.0);
    }

    @Test
    void toggleFlight_whenNotEligible_doesNotInterfere() {
        PlayerMock player = server.addPlayer("frank");
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
        PlayerMock player = server.addPlayer("grace");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5));
        player.setAllowFlight(true);

        // Mark player as flying via the toggle event
        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);
        assertThat(listener.isFlying(player.getUniqueId())).isTrue();

        EntityDamageEvent dmg = new EntityDamageEvent(player,
                EntityDamageEvent.DamageCause.FALL, 5.0);
        server.getPluginManager().callEvent(dmg);

        assertThat(dmg.isCancelled()).isTrue();
    }

    @Test
    void entityDamage_flyIntoWallWhileGliding_cancelled() {
        PlayerMock player = server.addPlayer("henry");
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
        PlayerMock player = server.addPlayer("iris");
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
        PlayerMock player = server.addPlayer("jack");
        // Not in flying set

        EntityDamageEvent dmg = new EntityDamageEvent(player,
                EntityDamageEvent.DamageCause.FALL, 5.0);
        server.getPluginManager().callEvent(dmg);

        assertThat(dmg.isCancelled()).isFalse();
    }

    @Test
    void configReload_newSpawnRadius_isHonored() {
        // Player at distance 35 from spawn — outside the original radius of 30.
        PlayerMock player = server.addPlayer("kate");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 35, 64, 0));

        scheduler.advance(Duration.ofMillis(150));
        assertThat(player.getAllowFlight()).isFalse(); // outside original radius

        // Simulate config reload: widen spawn radius to 50.
        config = makeConfig(50, 3);

        scheduler.advance(Duration.ofMillis(150));
        assertThat(player.getAllowFlight()).isTrue(); // now inside reloaded radius
    }

    // ── glide boost eligibility ──────────────────────────────────────────────

    @Test
    void glideStart_inSpawnZone_withBoostEnabled_marksBoostEligible() {
        config = makeConfigWithBoost(30, 3, true, 2.0, 5);
        PlayerMock player = server.addPlayer("lily");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5)); // inside spawn radius
        player.setAllowFlight(true);

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(event);

        assertThat(listener.isFlying(player.getUniqueId())).isTrue();
        assertThat(listener.isBoostEligible(player.getUniqueId())).isTrue();
    }

    @Test
    void glideStart_outsideSpawnZone_doesNotMarkBoostEligible() {
        config = makeConfigWithBoost(30, 3, true, 2.0, 5);
        PlayerMock player = server.addPlayer("mike");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 200, 64, 200)); // far outside

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(event);

        assertThat(listener.isBoostEligible(player.getUniqueId())).isFalse();
    }

    @Test
    void sneak_whileEligibleGliding_appliesBoost() {
        config = makeConfigWithBoost(30, 3, true, 2.0, 1);
        PlayerMock player = server.addPlayer("nina");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5, 0f, 0f));
        player.setAllowFlight(true);

        // Start glide inside spawn zone → boost-eligible
        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);
        assertThat(listener.isBoostEligible(player.getUniqueId())).isTrue();

        // Player moves outside zone but stays gliding
        player.setLocation(new Location(world, 200, 64, 200, 0f, 0f));
        player.setGliding(true);

        // Trigger sneak boost
        org.bukkit.event.player.PlayerToggleSneakEvent sneak =
                new org.bukkit.event.player.PlayerToggleSneakEvent(player, true);
        server.getPluginManager().callEvent(sneak);

        assertThat(player.getVelocity().length()).isGreaterThan(0.0);
    }

    @Test
    void sneak_whenNotEligible_doesNotBoost() {
        config = makeConfigWithBoost(30, 3, true, 2.0, 5);
        PlayerMock player = server.addPlayer("otto");
        player.setGliding(true);
        // player is NOT in boostEligible (did not start glide through the listener)

        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        org.bukkit.event.player.PlayerToggleSneakEvent sneak =
                new org.bukkit.event.player.PlayerToggleSneakEvent(player, true);
        server.getPluginManager().callEvent(sneak);

        assertThat(player.getVelocity().length()).isEqualTo(0.0);
    }

    @Test
    void glideEnd_clearsBoostEligibility() {
        config = makeConfigWithBoost(30, 3, true, 2.0, 5);
        PlayerMock player = server.addPlayer("penny");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5));
        player.setAllowFlight(true);

        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);
        assertThat(listener.isBoostEligible(player.getUniqueId())).isTrue();

        // Glide ends
        org.bukkit.event.entity.EntityToggleGlideEvent endGlide =
                new org.bukkit.event.entity.EntityToggleGlideEvent(player, false);
        server.getPluginManager().callEvent(endGlide);

        assertThat(listener.isBoostEligible(player.getUniqueId())).isFalse();
        assertThat(listener.isFlying(player.getUniqueId())).isFalse();
    }

    @Test
    void playerQuit_clearsBoostEligibility() {
        config = makeConfigWithBoost(30, 3, true, 2.0, 5);
        PlayerMock player = server.addPlayer("quinn");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5));
        player.setAllowFlight(true);

        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);
        assertThat(listener.isBoostEligible(player.getUniqueId())).isTrue();

        // Player quits — MockBukkit fires PlayerQuitEvent via disconnect()
        player.disconnect();

        assertThat(listener.isBoostEligible(player.getUniqueId())).isFalse();
    }

    @Test
    void sneak_duringCooldown_doesNotBoost() {
        // cooldown of 60 seconds — second sneak in the same session must be ignored
        config = makeConfigWithBoost(30, 3, true, 2.0, 60);
        PlayerMock player = server.addPlayer("rose");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5, 0f, 0f));
        player.setAllowFlight(true);

        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);

        // Move outside zone and stay gliding so the zone-exit guard allows the boost
        player.setLocation(new Location(world, 200, 64, 200, 0f, 0f));
        player.setGliding(true);

        // First sneak → boost applied
        org.bukkit.event.player.PlayerToggleSneakEvent first =
                new org.bukkit.event.player.PlayerToggleSneakEvent(player, true);
        server.getPluginManager().callEvent(first);
        double firstMag = player.getVelocity().length();
        assertThat(firstMag).isGreaterThan(0.0);

        // Reset velocity and immediately sneak again (cooldown = 60s, not elapsed)
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        org.bukkit.event.player.PlayerToggleSneakEvent second =
                new org.bukkit.event.player.PlayerToggleSneakEvent(player, true);
        server.getPluginManager().callEvent(second);

        // Velocity must still be zero — cooldown blocked the second boost
        assertThat(player.getVelocity().length()).isEqualTo(0.0);
    }

    @Test
    void sneak_whileStillInSpawnZone_doesNotBoost() {
        // Boost only applies AFTER leaving the zone, not while still inside.
        config = makeConfigWithBoost(30, 3, true, 2.0, 1);
        PlayerMock player = server.addPlayer("sam");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5, 0f, 0f)); // inside zone
        player.setAllowFlight(true);

        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);
        player.setGliding(true);
        // Player stays inside the zone — zone-exit guard must block the boost.
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

        org.bukkit.event.player.PlayerToggleSneakEvent sneak =
                new org.bukkit.event.player.PlayerToggleSneakEvent(player, true);
        server.getPluginManager().callEvent(sneak);

        assertThat(player.getVelocity().length()).isEqualTo(0.0);
    }

    @Test
    void configReload_glideBoostEnabled_isHonored() {
        // Start with boost disabled
        config = makeConfigWithBoost(30, 3, false, 2.0, 1);
        PlayerMock player = server.addPlayer("tara");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5, 0f, 0f));
        player.setAllowFlight(true);

        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);
        assertThat(listener.isBoostEligible(player.getUniqueId())).isFalse();

        // Simulate config reload: enable boost with cooldown 1s
        config = makeConfigWithBoost(30, 3, true, 3.0, 1);

        // New glide for a second player → should now be marked eligible
        PlayerMock player2 = server.addPlayer("uma");
        player2.setGameMode(GameMode.SURVIVAL);
        player2.setLocation(new Location(world, 5, 64, 5, 0f, 0f));
        player2.setAllowFlight(true);

        PlayerToggleFlightEvent toggle2 = new PlayerToggleFlightEvent(player2, true);
        server.getPluginManager().callEvent(toggle2);
        assertThat(listener.isBoostEligible(player2.getUniqueId())).isTrue();
    }
}
