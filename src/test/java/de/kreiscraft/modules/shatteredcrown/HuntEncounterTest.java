package de.kreiscraft.modules.shatteredcrown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.kreiscraft.core.command.CommandRegistrar;
import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.ConfigurateMessageService;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.testsupport.MockScheduler;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Mob;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

/** Public encounter events, player health, world indicators and native phase outcomes. */
class HuntEncounterTest {
    @org.junit.jupiter.api.extension.RegisterExtension
    static final org.junit.jupiter.api.extension.TestExecutionExceptionHandler NO_SILENT_SKIPS = (context, error) -> {
        if (error instanceof org.opentest4j.TestAbortedException) {
            throw new AssertionError("Unsupported MockBukkit behavior needs an explicit Paper boundary", error);
        }
        throw error;
    };
    @TempDir
    Path data;
    private ServerMock server;
    private PluginMock plugin;
    private WorldMock end;
    private EnderDragon dragon;
    private PlayerMock player;
    private MockScheduler scheduler;
    private ShatteredCrownModule module;
    private ConfigurationNode config;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        end = server.addSimpleWorld("arena");
        end.setEnvironment(World.Environment.THE_END);
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                end.getBlockAt(x, 63, z).setType(Material.END_STONE);
            }
        }
        dragon = end.spawn(new Location(end, 0, 80, 0), EnderDragon.class);
        dragon.setPodium(new Location(end, 0, 64, 0));
        dragon.setPhase(EnderDragon.Phase.CIRCLING);
        player = server.addPlayer();
        player.teleport(new Location(end, 10.5, 64, 0.5));
        scheduler = new MockScheduler();
        module = new ShatteredCrownModule(new DragonBattleAccess() {
            @Override
            public org.bukkit.boss.DragonBattle battle(World world) {
                return world == end ? dragon.getDragonBattle() : null;
            }

            @Override
            public void preventDespawn(Mob defender) {
                defender.registerAttribute(Attribute.ATTACK_DAMAGE);
                defender.registerAttribute(Attribute.SPAWN_REINFORCEMENTS);
            }

            @Override
            public void faceWarningToViewer(TextDisplay warning) {
                // Only the unsupported billboard setter is omitted.
            }

            @Override
            public void bombardmentDamage(org.bukkit.entity.Player recipient, EnderDragon source, double amount) {
                new org.mockbukkit.mockbukkit.simulate.entity.LivingEntitySimulation((PlayerMock) recipient)
                        .simulateDamage(amount, DamageSource.builder(DamageType.DRAGON_BREATH)
                                .withCausingEntity(source).build());
            }
        });
        config = BasicConfigurationNode.root();
    }

    @AfterEach
    void tearDown() {
        module.disable();
        MockBukkit.unmock();
    }

    private void hunt() throws Exception {
        config.node("enabled").set(true);
        config.node("world-key").set(end.getKey().toString());
        module.enable(new ModuleContext(plugin, config,
                new ConfigurateMessageService(data, getClass().getClassLoader(), "en", server,
                        recipient -> recipient.getName().equals("Deutsch") ? java.util.Optional.of("de")
                                : java.util.Optional.empty()),
                new ServiceRegistry(), scheduler, mock(CommandRegistrar.class), new EventBus(plugin)));
        scheduler.advance(Duration.ZERO);
        end.getEntitiesByClass(Zombie.class).forEach(defender -> {
            defender.setHealth(0);
            server.getPluginManager().callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
        });
        end.getEntitiesByClass(EnderCrystal.class).forEach(this::hit);
    }

    private EntityDamageEvent hit(org.bukkit.entity.Entity target) {
        var event = new EntityDamageByEntityEvent(player, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                mock(DamageSource.class), 10);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void fixedLaneWarnsBeforeDamageAndExpiresWithItsGroundUnchanged() throws Exception {
        hunt();
        assertThat(hit(dragon).isCancelled()).isFalse();
        scheduler.advance(Duration.ofMillis(9999));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofMillis(1));
        var warning = end.getEntitiesByClass(TextDisplay.class).iterator().next();
        assertThat(warning.getLocation().getX()).isEqualTo(10.5);
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertThat(plain.serialize(warning.text())).contains("3");
        scheduler.advance(Duration.ofMillis(2999));
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(player.getHealth()).isEqualTo(16);
        assertThat(plain.serialize(warning.text())).contains("6");
        player.teleport(new Location(end, 10.5, 64, 4.51));
        scheduler.advance(Duration.ofSeconds(6));
        assertThat(warning.isValid()).isFalse();
        assertThat(player.getHealth()).isEqualTo(16);
        assertThat(end.getBlockAt(10, 63, 0).getType()).isEqualTo(Material.END_STONE);
    }

    @Test
    void activeLaneCannotBeCoveredAgainAndDamageIsLimitedToOnePulsePerCadence() throws Exception {
        config.node("lane-cadence-seconds").set(5);
        hunt();
        scheduler.advance(Duration.ofSeconds(8));
        assertThat(player.getHealth()).isEqualTo(16);
        player.setHealth(20);
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        assertThat(player.getHealth()).isEqualTo(12);
        player.teleport(new Location(end, 10.5, 64, 4.51));
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(player.getHealth()).isEqualTo(12);
    }

    private void nativePhase(EnderDragon.Phase phase) {
        // EnderDragonMock has no AI and its setter does not emit Paper's public event.
        var event = new org.bukkit.event.entity.EnderDragonChangePhaseEvent(dragon, dragon.getPhase(), phase);
        server.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            dragon.setPhase(event.getNewPhase());
        }
    }

    @Test
    void landingWaitsForNativeArrivalThenReservesTwelveSecondsOfMeleeTimeBeforeTakingOff() throws Exception {
        config.node("lane-damage").set(0);
        hunt();
        scheduler.advance(Duration.ofMillis(29999));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.FLY_TO_PORTAL);
        scheduler.advance(Duration.ofSeconds(5));
        dragon.teleport(new Location(end, 0, 67, 0));
        nativePhase(EnderDragon.Phase.LAND_ON_PORTAL);
        nativePhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        scheduler.advance(Duration.ofMillis(250));
        assertThat(hit(dragon).isCancelled()).isFalse();
        nativePhase(EnderDragon.Phase.LEAVE_PORTAL);
        nativePhase(EnderDragon.Phase.BREATH_ATTACK);
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        scheduler.advance(Duration.ofMillis(11999));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofMillis(1));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.LEAVE_PORTAL);
        assertThat(player.getHealth()).isEqualTo(20);
    }

    @Test
    void overdueLandingReservesTheNextClearIntervalInsteadOfBeingStarvedByLongLanes() throws Exception {
        config.node("lane-lifetime-seconds").set(20);
        config.node("lane-damage").set(0);
        hunt();
        scheduler.advance(Duration.ofSeconds(30));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        scheduler.advance(Duration.ofMillis(3250));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.FLY_TO_PORTAL);
    }

    @Test
    void clearGroundInsideTheLaneStillNeedsAReachablePlayerSizedEscapeOutsideIt() throws Exception {
        hunt();
        // Keep the complete warned rectangle but remove every route beyond its short sides.
        for (int x = -3; x <= 23; x++) {
            for (int z = -10; z <= 10; z++) {
                if (z < -3 || z > 3) {
                    end.getBlockAt(x, 63, z).setType(Material.AIR);
                }
            }
        }
        scheduler.advance(Duration.ofSeconds(13));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(player.getHealth()).isEqualTo(20);
        // A real northward walking corridor makes the same attack fair on the next cadence.
        for (int x = 9; x <= 11; x++) {
            for (int z = -8; z <= 0; z++) {
                end.getBlockAt(x, 63, z).setType(Material.END_STONE);
            }
        }
        scheduler.advance(Duration.ofSeconds(7));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        player.teleport(new Location(end, 10.5, 64, -5.5));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(20);
    }

    @Test
    void laneFootprintMatchesTheWarnedRectangleIncludingItsEndsAndHeight() throws Exception {
        hunt();
        scheduler.advance(Duration.ofSeconds(10));
        var edge = server.addPlayer();
        edge.teleport(new Location(end, 22.5, 64, 3.5));
        var beyondEnd = server.addPlayer();
        beyondEnd.teleport(new Location(end, 22.51, 64, 0.5));
        var beyondSide = server.addPlayer();
        beyondSide.teleport(new Location(end, 10.5, 64, 3.51));
        var above = server.addPlayer();
        above.teleport(new Location(end, 10.5, 68, 0.5));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(edge.getHealth()).isEqualTo(16);
        assertThat(beyondEnd.getHealth()).isEqualTo(20);
        assertThat(beyondSide.getHealth()).isEqualTo(20);
        assertThat(above.getHealth()).isEqualTo(20);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"warning", "active", "approach", "landed"})
    void disablingEveryHuntStageCancelsDamageAndRestoresNativeFlight(String stage) throws Exception {
        if (stage.equals("approach") || stage.equals("landed")) {
            config.node("lane-damage").set(0);
        }
        hunt();
        scheduler.advance(Duration.ofSeconds(stage.equals("warning") ? 10 : stage.equals("active") ? 13 : 30));
        if (stage.equals("landed")) {
            dragon.teleport(new Location(end, 0, 67, 0));
            nativePhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
            scheduler.advance(Duration.ofMillis(250));
        }
        double health = player.getHealth();
        module.disable();
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        scheduler.advance(Duration.ofSeconds(120));
        assertThat(player.getHealth()).isEqualTo(health);
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
    }

    @Test
    void expiryIsExclusiveSoThereIsNoExtraDamageAtTheLifetimeBoundary() throws Exception {
        config.node("lane-lifetime-seconds").set(1);
        config.node("lane-damage").set(1);
        hunt();
        scheduler.advance(Duration.ofSeconds(13));
        assertThat(player.getHealth()).isEqualTo(19);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(player.getHealth()).isEqualTo(19);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void existingNativeBreathBlocksLanesAndLandingUntilItsExpiry() throws Exception {
        config.node("lane-damage").set(0);
        hunt();
        var pool = end.spawn(player.getLocation(), org.bukkit.entity.AreaEffectCloud.class);
        pool.setParticle(org.bukkit.Particle.DRAGON_BREATH);
        pool.setRadius(3);
        scheduler.advance(Duration.ofSeconds(30));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        pool.remove();
        scheduler.advance(Duration.ofMillis(250));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.FLY_TO_PORTAL);
        assertThat(player.getHealth()).isEqualTo(20);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"arrival", "world", "death", "quit", "creative", "spectator"})
    void invalidatingTheWarnedParticipantCannotLeaveAnUnannouncedLane(String departure) throws Exception {
        hunt();
        scheduler.advance(Duration.ofSeconds(10));
        var warning = end.getEntitiesByClass(TextDisplay.class).iterator().next();
        var teammate = server.addPlayer();
        teammate.teleport(player.getLocation());
        switch (departure) {
            case "arrival" -> player.teleport(new Location(end, 100, 50, 0));
            case "world" -> player.teleport(new Location(server.addSimpleWorld("other"), 0, 64, 0));
            case "death" -> player.setHealth(0);
            case "quit" -> player.kick();
            case "creative" -> player.setGameMode(org.bukkit.GameMode.CREATIVE);
            case "spectator" -> player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            default -> throw new AssertionError(departure);
        }
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(warning.isValid()).isFalse();
        assertThat(teammate.getHealth()).isEqualTo(20);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"victory-warning", "victory-active", "victory-landed",
            "unload-warning", "unload-active", "unload-landed", "reset-warning", "reset-active", "reset-landed"})
    void everyTerminationCancelsWarningsHazardsAndLandingWithoutDeletingUnrelatedEntities(String stage)
            throws Exception {
        config.node("lane-damage").set(0);
        hunt();
        var unrelated = end.spawn(new Location(end, 50, 64, 0), TextDisplay.class);
        scheduler.advance(Duration.ofSeconds(stage.endsWith("warning") ? 10 : stage.endsWith("active") ? 13 : 30));
        if (stage.endsWith("landed")) {
            dragon.teleport(new Location(end, 0, 67, 0));
            nativePhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
            scheduler.advance(Duration.ofMillis(250));
        }
        if (stage.startsWith("victory")) {
            server.getPluginManager().callEvent(new EntityDeathEvent(dragon, mock(DamageSource.class), List.of()));
        } else if (stage.startsWith("unload")) {
            server.getPluginManager().callEvent(new org.bukkit.event.world.WorldUnloadEvent(end));
        } else {
            player.teleport(new Location(end, 100, 50, 0));
            scheduler.advance(Duration.ofSeconds(60));
        }
        scheduler.advance(Duration.ofSeconds(60));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).containsExactly(unrelated);
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(dragon.isValid()).isTrue();
        if (!stage.startsWith("victory")) {
            assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        }
    }

    @Test
    void cancellingNativeCombatPreservesHealthAndDoesNotDisablePermittedRangedFlightDamage() throws Exception {
        hunt();
        var arrow = end.spawn(player.getLocation(), org.bukkit.entity.Arrow.class);
        arrow.setShooter(player);
        var ranged = new EntityDamageByEntityEvent(arrow, dragon, EntityDamageEvent.DamageCause.PROJECTILE,
                mock(DamageSource.class), 10);
        server.getPluginManager().callEvent(ranged);
        assertThat(ranged.isCancelled()).isFalse();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void protect(EntityDamageEvent event) {
                event.setCancelled(true);
            }
        }, plugin);
        scheduler.advance(Duration.ofSeconds(19));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void protectedArrivalsAndIneligiblePlayersCannotReceiveLaneDamage() throws Exception {
        config.node("arrival-x").set(30);
        config.node("arrival-y").set(64);
        config.node("arrival-radius").set(3);
        hunt();
        scheduler.advance(Duration.ofSeconds(10));
        var arrival = server.addPlayer();
        arrival.teleport(new Location(end, 30, 64, 0));
        var creative = server.addPlayer();
        creative.teleport(player.getLocation());
        creative.setGameMode(org.bukkit.GameMode.CREATIVE);
        var spectator = server.addPlayer();
        spectator.teleport(player.getLocation());
        spectator.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.teleport(arrival.getLocation());
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(arrival.getHealth()).isEqualTo(20);
        assertThat(creative.getHealth()).isEqualTo(20);
        assertThat(spectator.getHealth()).isEqualTo(20);
    }

    @Test
    void vanishedWarningAndDisableFromDamageNeverResumeInvisibleOrPostCleanupDamage() throws Exception {
        hunt();
        scheduler.advance(Duration.ofSeconds(10));
        end.getEntitiesByClass(TextDisplay.class).iterator().next().remove();
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(20);
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void stop(EntityDamageEvent event) {
                if (event.getEntity() instanceof org.bukkit.entity.Player) {
                    event.setCancelled(true);
                    module.disable();
                }
            }
        }, plugin);
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void huntWarningsAndLandingInstructionsRespectEachPlayersLanguage() throws Exception {
        config.node("lane-damage").set(0);
        var german = server.addPlayer("Deutsch");
        german.teleport(player.getLocation());
        hunt();
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(actionBars(player)).anyMatch(text -> text.contains("Breath lane in 3.0s"));
        assertThat(actionBars(german)).anyMatch(text -> text.contains("Atembahn in 3.0s"));
        scheduler.advance(Duration.ofSeconds(20));
        assertThat(messages(player)).anyMatch(text -> text.contains("approaching the podium"));
        assertThat(messages(german)).anyMatch(text -> text.contains("nähert sich dem Podium"));
        dragon.teleport(new Location(end, 0, 67, 0));
        nativePhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        scheduler.advance(Duration.ofMillis(250));
        assertThat(messages(player)).anyMatch(text -> text.contains("has landed"));
        assertThat(messages(german)).anyMatch(text -> text.contains("ist gelandet"));
    }

    private List<String> messages(PlayerMock recipient) {
        var result = new java.util.ArrayList<String>();
        String message;
        while ((message = recipient.nextMessage()) != null) {
            result.add(message);
        }
        return result;
    }

    private List<String> actionBars(PlayerMock recipient) {
        var result = new java.util.ArrayList<String>();
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        net.kyori.adventure.text.Component bar;
        while ((bar = recipient.nextActionBar()) != null) {
            result.add(plain.serialize(bar));
        }
        return result;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"lane-width,0", "lane-length,41", "lane-warning-seconds,1",
            "lane-lifetime-seconds,21", "lane-damage,NaN", "lane-cadence-seconds,3",
            "lane-damage-cadence-seconds,0", "landing-window-seconds,7", "landing-cadence-seconds,0",
            "landing-approach-seconds,Infinity", "landing-radius,25"})
    void invalidHuntReloadIsAtomicAndKeepsTheWarnedProfile(String key, double value) throws Exception {
        hunt();
        scheduler.advance(Duration.ofSeconds(10));
        var invalid = config.copy();
        invalid.node(key).set(value);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> module.onConfigReload(invalid))
                .isInstanceOf(RuntimeException.class);
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(16);
    }

    @Test
    void validHuntReloadLeavesTheCurrentWarningAloneAndAppliesOnTheNextAttempt() throws Exception {
        hunt();
        scheduler.advance(Duration.ofSeconds(10));
        var next = config.copy();
        next.node("lane-damage").set(1);
        next.node("lane-warning-seconds").set(2);
        next.node("lane-width").set(4);
        module.onConfigReload(next);
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(16);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(60));
        player.teleport(new Location(end, 10.5, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(1));
        end.getEntitiesByClass(Zombie.class).forEach(defender -> {
            defender.setHealth(0);
            server.getPluginManager().callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
        });
        end.getEntitiesByClass(EnderCrystal.class).forEach(this::hit);
        player.setHealth(20);
        scheduler.advance(Duration.ofMillis(11999));
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(player.getHealth()).isEqualTo(19);
    }

    @Test
    void representativeTwentyPlayerHuntsAndRepeatedResetsDoNotAccumulateEffectsOrForcedPhases() throws Exception {
        config.node("lane-damage").set(0);
        for (int count = 1; count < 20; count++) {
            var teammate = server.addPlayer();
            teammate.teleport(player.getLocation());
        }
        hunt();
        for (int attempt = 0; attempt < 4; attempt++) {
            // Five minutes of real public native-phase arrivals per attempt, not a private attack driver.
            for (int second = 0; second < 300; second++) {
                scheduler.advance(Duration.ofSeconds(1));
                if (dragon.getPhase() == EnderDragon.Phase.FLY_TO_PORTAL) {
                    dragon.teleport(new Location(end, 0, 67, 0));
                    nativePhase(EnderDragon.Phase.LAND_ON_PORTAL);
                    nativePhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
                } else if (dragon.getPhase() == EnderDragon.Phase.LEAVE_PORTAL) {
                    nativePhase(EnderDragon.Phase.CIRCLING);
                }
                assertThat(end.getEntitiesByClass(TextDisplay.class).size()).isLessThanOrEqualTo(2);
                assertThat(end.getPlayers())
                        .allSatisfy(participant -> assertThat(participant.getHealth()).isEqualTo(20));
            }
            end.getPlayers().forEach(participant -> participant.teleport(new Location(end, 100, 50, 0)));
            scheduler.advance(Duration.ofSeconds(60));
            assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
            assertThat(end.getEntitiesByClass(EnderCrystal.class)).isEmpty();
            assertThat(end.getEntitiesByClass(Zombie.class).stream().filter(org.bukkit.entity.Entity::isValid))
                    .isEmpty();
            assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
            end.getPlayers().forEach(participant -> participant.teleport(new Location(end, 10.5, 64, 0.5)));
            scheduler.advance(Duration.ofSeconds(1));
            end.getEntitiesByClass(Zombie.class).stream().filter(org.bukkit.entity.Entity::isValid)
                    .forEach(defender -> {
                        defender.setHealth(0);
                        server.getPluginManager()
                                .callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
                    });
            end.getEntitiesByClass(EnderCrystal.class).forEach(this::hit);
        }
    }

    @Test
    void aSittingPhaseWhileStillAwayFromThePodiumCannotStartTheMeleeWindow() throws Exception {
        config.node("lane-damage").set(0);
        hunt();
        scheduler.advance(Duration.ofSeconds(30));
        dragon.teleport(new Location(end, 40, 90, 0));
        nativePhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        scheduler.advance(Duration.ofMillis(250));
        assertThat(messages(player)).noneMatch(text -> text.contains("has landed"));
        scheduler.advance(Duration.ofMillis(19750));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.LEAVE_PORTAL);
    }

    @Test
    void twoDisjointTemporaryLanesCanCoexistWithoutDoublingDamageAndAThirdIsDeferred() throws Exception {
        config.node("lane-cadence-seconds").set(5);
        config.node("lane-lifetime-seconds").set(20);
        config.node("lane-damage").set(1);
        hunt();
        scheduler.advance(Duration.ofSeconds(8));
        assertThat(player.getHealth()).isEqualTo(19);
        player.teleport(new Location(end, -10.5, 64, 20.5));
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(2);
        assertThat(player.getHealth()).isEqualTo(18);
        player.teleport(new Location(end, -20.5, 64, -20.5));
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(2);
        assertThat(player.getHealth()).isEqualTo(18);
        scheduler.advance(Duration.ofSeconds(13));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
    }

    @Test
    void refusingWarningSpawnNeverLeavesInvisibleDamageOrDelayedWork() throws Exception {
        hunt();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void veto(org.bukkit.event.entity.EntitySpawnEvent event) {
                if (event.getEntity() instanceof TextDisplay) {
                    event.setCancelled(true);
                }
            }
        }, plugin);
        scheduler.advance(Duration.ofSeconds(19));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(player.getHealth()).isEqualTo(20);
    }

    @Test
    void theLaneDamageHeightCannotOverlapArrivalProtectionAboveOtherwiseUsableGround() throws Exception {
        config.node("arrival-x").set(10.5);
        config.node("arrival-y").set(69);
        config.node("arrival-height").set(4);
        config.node("arrival-radius").set(3);
        hunt();
        scheduler.advance(Duration.ofSeconds(13));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(player.getHealth()).isEqualTo(20);
    }
}
