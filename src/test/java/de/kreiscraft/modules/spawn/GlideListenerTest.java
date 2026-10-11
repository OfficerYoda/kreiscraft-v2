package de.kreiscraft.modules.spawn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.config.YamlConfigService;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.testsupport.MockScheduler;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
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
    private MessageService messages;
    private DefaultSpawnService service;
    private MockScheduler scheduler;
    private GlideListener listener;

    /** Mutable config reference — listeners read fresh values on every event. */
    private SpawnConfig config;

    private SpawnConfig makeConfig(int spawnRadius, int multiplyValue) {
        return makeConfigWithBoost(spawnRadius, multiplyValue, false, 2.0);
    }

    private SpawnConfig makeDisabledConfig(int spawnRadius, int multiplyValue) {
        return new SpawnConfig(
                spawnRadius,
                new SpawnConfig.GlideConfig(false, multiplyValue),
                new SpawnConfig.GlideBoostConfig(false, 2.0));
    }

    private SpawnConfig makeConfigWithBoost(int spawnRadius, int multiplyValue,
            boolean boostEnabled, double boostStrength) {
        return new SpawnConfig(
                spawnRadius,
                new SpawnConfig.GlideConfig(true, multiplyValue),
                new SpawnConfig.GlideBoostConfig(boostEnabled, boostStrength));
    }

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");

        messages = mock(MessageService.class);
        when(messages.render(anyString())).thenReturn(Component.empty());

        YamlConfigService configService =
                new YamlConfigService(tmp, getClass().getClassLoader());
        config = makeConfig(30, 3);
        service = new DefaultSpawnService(configService, () -> config);

        // Set spawn at origin in the world
        service.setSpawn(new Location(world, 0, 64, 0));

        scheduler = new MockScheduler();
        listener = new GlideListener(service, messages, () -> config, scheduler, server);
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

        // Advance 5 ticks (250ms) to fire the repeating task
        scheduler.advance(Duration.ofMillis(250));

        assertThat(player.getAllowFlight()).isTrue();
    }

    @Test
    void tickTask_revokesAllowFlight_outsideRadius_forSurvivalPlayer() {
        PlayerMock player = server.addPlayer("bob");
        player.setGameMode(GameMode.SURVIVAL);
        // Move player far outside spawn radius
        player.setLocation(new Location(world, 100, 64, 100));

        scheduler.advance(Duration.ofMillis(250));

        assertThat(player.getAllowFlight()).isFalse();
    }

    @Test
    void tickTask_doesNotRevokeAllowFlight_forCreativePlayer() {
        PlayerMock player = server.addPlayer("charlie");
        player.setGameMode(GameMode.CREATIVE);
        // Creative player outside spawn radius — should NOT have flight revoked
        player.setLocation(new Location(world, 100, 64, 100));
        player.setAllowFlight(true); // creative normally has this

        scheduler.advance(Duration.ofMillis(250));

        // Listener must not touch creative player's flight state
        assertThat(player.getAllowFlight()).isTrue();
        assertThat(listener.isFlying(player.getUniqueId())).isFalse();
    }

    @Test
    void tickTask_usesFullThreeDimensions_forGlideZone() {
        PlayerMock player = server.addPlayer("vera");
        player.setGameMode(GameMode.SURVIVAL);
        // Horizontal distance 0 but vertical 40: outside the 3D Glide Zone of radius 30.
        player.setLocation(new Location(world, 0, 104, 0));

        scheduler.advance(Duration.ofMillis(250));

        assertThat(player.getAllowFlight()).isFalse();
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
    void toggleFlight_appliesDirectionalMultiplyValue() {
        // Config has multiplyValue = 3 and a unit facing vector → speed 3 blocks/tick.
        PlayerMock player = server.addPlayer("eve");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5, 0f, 0f)); // looking along +z
        player.setAllowFlight(true);

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(event);

        assertThat(player.getVelocity().length())
                .isCloseTo(3.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(player.getVelocity().getX()).isCloseTo(0.0,
                org.assertj.core.data.Offset.offset(0.01));
        assertThat(player.getVelocity().getZ()).isCloseTo(3.0,
                org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void toggleFlight_outsideGlideZone_reportsRejection() {
        PlayerMock player = server.addPlayer("frank");
        player.setGameMode(GameMode.SURVIVAL);
        // Far from spawn
        player.setLocation(new Location(world, 200, 64, 200));

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(listener.isFlying(player.getUniqueId())).isFalse();
        verify(messages).send(player, "spawn.glide-outside-zone");
    }

    @Test
    void toggleFlight_outsideZoneOnlyVertically_reportsRejection() {
        PlayerMock player = server.addPlayer("victor");
        player.setGameMode(GameMode.SURVIVAL);
        // Horizontal distance 0 (well inside the Spawn Access Zone) but vertical 40, so outside
        // the 3D Glide Zone of radius 30.
        player.setLocation(new Location(world, 0, 104, 0));

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(listener.isFlying(player.getUniqueId())).isFalse();
        verify(messages).send(player, "spawn.glide-outside-zone");
    }

    @Test
    void toggleFlight_inDifferentWorld_doesNotReport() {
        World nether = server.addSimpleWorld("world_nether");
        PlayerMock player = server.addPlayer("walter");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(nether, 0, 64, 0));

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
        verify(messages, never()).send(player, "spawn.glide-outside-zone");
    }

    @Test
    void toggleFlight_creativePlayer_isIgnored() {
        PlayerMock player = server.addPlayer("cora");
        player.setGameMode(GameMode.CREATIVE);
        player.setLocation(new Location(world, 200, 64, 200));

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(event);

        verify(messages, never()).send(player, "spawn.glide-outside-zone");
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

        scheduler.advance(Duration.ofMillis(250));
        assertThat(player.getAllowFlight()).isFalse(); // outside original radius

        // Simulate config reload: widen spawn radius to 50.
        config = makeConfig(50, 3);

        scheduler.advance(Duration.ofMillis(250));
        assertThat(player.getAllowFlight()).isTrue(); // now inside reloaded radius
    }

    @Test
    void configReload_enablesGliding() {
        PlayerMock player = server.addPlayer("reload-enable");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5));
        config = makeDisabledConfig(30, 3);

        scheduler.advance(Duration.ofMillis(250));
        assertThat(player.getAllowFlight()).isFalse();

        config = makeConfig(30, 3);
        listener.reload();

        assertThat(player.getAllowFlight()).isTrue();
    }

    @Test
    void configReload_disablesGlidingAndClearsManagedState() {
        PlayerMock player = server.addPlayer("reload-disable");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5));
        player.setAllowFlight(true);
        server.getPluginManager().callEvent(new PlayerToggleFlightEvent(player, true));
        assertThat(listener.isFlying(player.getUniqueId())).isTrue();

        config = makeDisabledConfig(30, 3);
        listener.reload();

        assertThat(player.getAllowFlight()).isFalse();
        assertThat(listener.isFlying(player.getUniqueId())).isFalse();
    }

    // ── glide boost eligibility ──────────────────────────────────────────────

    @Test
    void glideStart_inGlideZone_withBoostEnabled_marksBoostEligible() {
        config = makeConfigWithBoost(30, 3, true, 2.0);
        PlayerMock player = server.addPlayer("lily");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5)); // inside glide zone
        player.setAllowFlight(true);

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(event);

        assertThat(listener.isFlying(player.getUniqueId())).isTrue();
        assertThat(listener.isBoostEligible(player.getUniqueId())).isTrue();
    }

    @Test
    void glideStart_outsideGlideZone_doesNotMarkBoostEligible() {
        config = makeConfigWithBoost(30, 3, true, 2.0);
        PlayerMock player = server.addPlayer("mike");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 200, 64, 200)); // far outside

        PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(event);

        assertThat(listener.isBoostEligible(player.getUniqueId())).isFalse();
    }

    @Test
    void swappingHands_neverAppliesGlideBoost() {
        config = makeConfigWithBoost(30, 3, true, 2.0);
        PlayerMock player = server.addPlayer("swapless");
        player.setGliding(true);
        player.setVelocity(new org.bukkit.util.Vector());

        PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(
                player, new ItemStack(Material.STONE), new ItemStack(Material.DIRT));
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(player.getVelocity().length()).isZero();
    }

    @Test
    void sneak_whileEligibleGliding_appliesBoost() {
        config = makeConfigWithBoost(30, 3, true, 2.0);
        PlayerMock player = server.addPlayer("nina");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5, 0f, 0f));
        player.setAllowFlight(true);

        // Start glide inside glide zone → boost-eligible
        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);
        assertThat(listener.isBoostEligible(player.getUniqueId())).isTrue();

        // Player moves outside zone but stays gliding
        player.setLocation(new Location(world, 200, 64, 200, 0f, 0f));
        player.setGliding(true);

        // Trigger sneak boost
        PlayerToggleSneakEvent sneak = new PlayerToggleSneakEvent(player, true);
        server.getPluginManager().callEvent(sneak);

        assertThat(player.getVelocity().length()).isGreaterThan(0.0);
    }

    @Test
    void sneak_whenNotEligible_doesNotBoost() {
        config = makeConfigWithBoost(30, 3, true, 2.0);
        PlayerMock player = server.addPlayer("otto");
        player.setGliding(true);
        // player is NOT in boostEligible (did not start glide through the listener)

        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        PlayerToggleSneakEvent sneak = new PlayerToggleSneakEvent(player, true);
        server.getPluginManager().callEvent(sneak);

        assertThat(player.getVelocity().length()).isEqualTo(0.0);
    }

    @Test
    void serverGlideStop_whileManagedPlayerIsAirborne_isCancelled() {
        config = makeConfigWithBoost(30, 3, true, 2.0);
        PlayerMock player = server.addPlayer("penny");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5));
        player.setAllowFlight(true);
        player.setOnGround(false);

        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);
        assertThat(listener.isBoostEligible(player.getUniqueId())).isTrue();

        EntityToggleGlideEvent endGlide = new EntityToggleGlideEvent(player, false);
        server.getPluginManager().callEvent(endGlide);

        assertThat(endGlide.isCancelled()).isTrue();
        assertThat(listener.isBoostEligible(player.getUniqueId())).isTrue();
        assertThat(listener.isFlying(player.getUniqueId())).isTrue();
    }

    @Test
    void serverGlideStop_afterLeavingGlideZone_isCancelled() {
        PlayerMock player = server.addPlayer("outside-zone");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5));
        player.setAllowFlight(true);
        player.setOnGround(false);

        server.getPluginManager().callEvent(new PlayerToggleFlightEvent(player, true));
        assertThat(listener.isFlying(player.getUniqueId())).isTrue();

        player.setLocation(new Location(world, 200, 64, 200));
        scheduler.advance(Duration.ofMillis(250));
        EntityToggleGlideEvent endGlide = new EntityToggleGlideEvent(player, false);
        server.getPluginManager().callEvent(endGlide);

        assertThat(player.getAllowFlight()).isFalse();
        assertThat(endGlide.isCancelled()).isTrue();
        assertThat(listener.isFlying(player.getUniqueId())).isTrue();
    }

    @Test
    void glideEnd_onLanding_clearsBoostEligibility() {
        config = makeConfigWithBoost(30, 3, true, 2.0);
        PlayerMock player = server.addPlayer("landed");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5));
        player.setAllowFlight(true);

        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);
        assertThat(listener.isBoostEligible(player.getUniqueId())).isTrue();

        player.setOnGround(true);
        EntityToggleGlideEvent endGlide = new EntityToggleGlideEvent(player, false);
        server.getPluginManager().callEvent(endGlide);

        assertThat(endGlide.isCancelled()).isFalse();
        assertThat(listener.isBoostEligible(player.getUniqueId())).isFalse();
        assertThat(listener.isFlying(player.getUniqueId())).isFalse();
    }

    @Test
    void playerQuit_clearsBoostEligibility() {
        config = makeConfigWithBoost(30, 3, true, 2.0);
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
    void shutdown_clearsBoostEligibility() {
        config = makeConfigWithBoost(30, 3, true, 2.0);
        PlayerMock player = server.addPlayer("shutdown");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5));

        server.getPluginManager().callEvent(new PlayerToggleFlightEvent(player, true));
        listener.shutdown();

        assertThat(listener.isBoostEligible(player.getUniqueId())).isFalse();
    }

    @Test
    void newEligibleGlide_grantsOneNewOpportunity() {
        config = makeConfigWithBoost(30, 3, true, 2.0);
        PlayerMock player = server.addPlayer("renewed");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5));

        server.getPluginManager().callEvent(new PlayerToggleFlightEvent(player, true));
        player.setGliding(true);
        server.getPluginManager().callEvent(new PlayerToggleSneakEvent(player, true));
        server.getPluginManager().callEvent(new EntityToggleGlideEvent(player, false));
        server.getPluginManager().callEvent(new PlayerToggleFlightEvent(player, true));

        assertThat(listener.isBoostEligible(player.getUniqueId())).isTrue();
    }

    @Test
    void secondSneak_duringSameGlide_doesNotBoost() {
        config = makeConfigWithBoost(30, 3, true, 2.0);
        PlayerMock player = server.addPlayer("rose");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5, 0f, 0f));
        player.setAllowFlight(true);

        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);

        // Move outside zone and stay gliding.
        player.setLocation(new Location(world, 200, 64, 200, 0f, 0f));
        player.setGliding(true);

        // First sneak → boost applied
        PlayerToggleSneakEvent first = new PlayerToggleSneakEvent(player, true);
        server.getPluginManager().callEvent(first);
        double firstMag = player.getVelocity().length();
        assertThat(firstMag).isGreaterThan(0.0);

        // Reset velocity and sneak again during the same glide.
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        PlayerToggleSneakEvent second = new PlayerToggleSneakEvent(player, true);
        server.getPluginManager().callEvent(second);

        // Velocity must still be zero — the one opportunity was consumed.
        assertThat(player.getVelocity().length()).isEqualTo(0.0);
    }

    @Test
    void sneak_whileStillInGlideZone_appliesBoostAndConsumesOpportunity() {
        config = makeConfigWithBoost(30, 3, true, 2.0);
        PlayerMock player = server.addPlayer("sam");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5, 0f, 0f)); // inside zone
        player.setAllowFlight(true);

        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);
        player.setGliding(true);
        // Player stays inside the zone.
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

        PlayerToggleSneakEvent sneak = new PlayerToggleSneakEvent(player, true);
        server.getPluginManager().callEvent(sneak);

        assertThat(player.getVelocity().length()).isGreaterThan(0.0);
        assertThat(listener.isBoostEligible(player.getUniqueId())).isFalse();
    }

    @Test
    void configReload_glideBoostEnabled_isHonored() {
        // Start with boost disabled
        config = makeConfigWithBoost(30, 3, false, 2.0);
        PlayerMock player = server.addPlayer("tara");
        player.setGameMode(GameMode.SURVIVAL);
        player.setLocation(new Location(world, 5, 64, 5, 0f, 0f));
        player.setAllowFlight(true);

        PlayerToggleFlightEvent toggle = new PlayerToggleFlightEvent(player, true);
        server.getPluginManager().callEvent(toggle);
        assertThat(listener.isBoostEligible(player.getUniqueId())).isFalse();

        // Simulate config reload: enable boost with cooldown 1s
        config = makeConfigWithBoost(30, 3, true, 3.0);

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
