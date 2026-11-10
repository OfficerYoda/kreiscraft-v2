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
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
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

/** The approved encounter boundary: real world effects, public events and controlled time. */
class OpeningEncounterTest {
    @org.junit.jupiter.api.extension.RegisterExtension
    static final org.junit.jupiter.api.extension.TestExecutionExceptionHandler NO_SILENT_SKIPS = (context, error) -> {
        if (error instanceof org.opentest4j.TestAbortedException) {
            throw new AssertionError("Unsupported MockBukkit behavior must be explicit at the Paper boundary", error);
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
        player = server.addPlayer();
        player.teleport(new Location(end, 10.5, 64, 0.5));
        scheduler = new MockScheduler();
        module = new ShatteredCrownModule(new DragonBattleAccess() {
            @Override
            public de.kreiscraft.platform.DragonFlightControl controlFlight(EnderDragon source, Runnable tick) {
                return new SimulatedDragonFlight(source, scheduler, tick);
            }

            @Override
            public org.bukkit.boss.DragonBattle battle(World world) {
                // WorldMock.getEnderDragonBattle is unimplemented; the dragon remains a world entity.
                return world == end ? dragon.getDragonBattle() : null;
            }

            @Override
            public void preventDespawn(Mob defender) {
                // Only the unsupported native despawn setter and absent Husk metadata are adapted.
                defender.registerAttribute(Attribute.ATTACK_DAMAGE);
                defender.registerAttribute(Attribute.SPAWN_REINFORCEMENTS);
            }

            @Override
            public void faceWarningToViewer(org.bukkit.entity.TextDisplay warning) {
                // DisplayMock.setBillboard is unimplemented; location, text and lifetime remain real.
            }

            @Override
            public void bombardmentDamage(org.bukkit.entity.Player recipient, EnderDragon source, double amount) {
                // LivingEntityMock.damage(DamageSource) is unimplemented; use its existing event/damage simulator.
                new org.mockbukkit.mockbukkit.simulate.entity.LivingEntitySimulation((PlayerMock) recipient)
                        .simulateDamage(amount, DamageSource.builder(org.bukkit.damage.DamageType.DRAGON_BREATH)
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

    private void enable() throws Exception {
        config.node("world-key").set(end.getKey().toString());
        config.node("enabled").set(true);
        module.enable(new ModuleContext(plugin, config,
                new ConfigurateMessageService(data, getClass().getClassLoader(), "en", server,
                        recipient -> recipient.getName().equals("Deutsch") ? java.util.Optional.of("de")
                                : java.util.Optional.empty()),
                new ServiceRegistry(), scheduler, mock(CommandRegistrar.class), new EventBus(plugin)));
        scheduler.advance(Duration.ZERO);
    }

    private List<EnderCrystal> anchors() {
        return end.getEntitiesByClass(EnderCrystal.class).stream().filter(Entity::isValid).toList();
    }

    private List<Zombie> defenders() {
        return end.getEntitiesByClass(Zombie.class).stream().filter(Entity::isValid).toList();
    }

    private EntityDamageEvent hit(Entity target) {
        var event = new EntityDamageByEntityEvent(player, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, mock(DamageSource.class), 10);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void defeat(Zombie defender) {
        defender.setHealth(0);
        server.getPluginManager().callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
    }

    @Test
    void fourSeparatedGroundAnchorsHaveIndependentDefenderGatesAndReleaseTheShieldInAnyOrder() throws Exception {
        enable();
        var objectives = anchors();
        assertThat(objectives).hasSize(4);
        assertThat(defenders()).hasSize(16);
        for (var anchor : objectives) {
            assertThat(anchor.getLocation().getY()).isEqualTo(64);
            assertThat(anchor.getLocation().getBlock().getRelative(0, -1, 0).getType()).isEqualTo(Material.END_STONE);
            assertThat(objectives.stream().filter(other -> other != anchor))
                    .allSatisfy(
                            other -> assertThat(other.getLocation().distance(anchor.getLocation())).isGreaterThan(12));
        }
        // This team's gate must not expose any of the other three objectives.
        var first = objectives.get(2);
        defenders().stream().filter(defender -> defender.getLocation().distance(first.getLocation()) < 5)
                .forEach(this::defeat);
        assertThat(first.isGlowing()).isTrue();
        assertThat(objectives.stream().filter(anchor -> anchor != first)).allMatch(anchor -> !anchor.isGlowing());
        hit(first);
        assertThat(first.isValid()).isFalse();
        assertThat(hit(dragon).isCancelled()).isTrue();

        // Remaining gates complete concurrently; crystal order is deliberately not spawn order.
        defenders().forEach(this::defeat);
        for (int index : List.of(3, 0, 1)) {
            var anchor = objectives.get(index);
            assertThat(anchor.isGlowing()).isTrue();
            hit(anchor);
            hit(anchor); // Duplicate damage notifications cannot advance another objective.
            assertThat(hit(dragon).isCancelled()).isEqualTo(index != 1);
        }
        assertThat(anchors()).isEmpty();
        assertThat(defenders()).isEmpty();
    }

    @Test
    void independentObjectivesAndDuplicateTransitionsDoNotProduceAutomaticOutput() throws Exception {
        var german = server.addPlayer("Deutsch");
        german.teleport(player.getLocation());
        enable();
        assertThat(anchors()).hasSize(4);
        assertThat(defenders()).hasSize(16);
        var objectives = anchors();
        var wave = defenders();
        wave.forEach(this::defeat);
        wave.forEach(defender -> server.getPluginManager()
                .callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of())));
        objectives.forEach(this::hit);
        objectives.forEach(this::hit);
        var english = drainMessages(player);
        var deutsch = drainMessages(german);
        assertThat(english).isEmpty();
        assertThat(deutsch).isEmpty();
        assertThat(drainActionBars(player)).isEmpty();
        assertThat(drainActionBars(german)).isEmpty();
        assertThat(hit(dragon).isCancelled()).isFalse();
    }

    private List<String> drainMessages(PlayerMock recipient) {
        var messages = new java.util.ArrayList<String>();
        String message;
        while ((message = recipient.nextMessage()) != null) {
            messages.add(message);
        }
        return messages;
    }

    @Test
    void bombardmentHasAVisibleFiniteWarningBeforeOneTerrainPreservingImpact() throws Exception {
        enable();
        scheduler.advance(Duration.ofMillis(7999));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofMillis(1));
        var indicators = end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class);
        assertThat(indicators).hasSize(1);
        var indicator = indicators.iterator().next();
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertThat(plain.serialize(indicator.text())).contains("3");
        assertThat(indicator.getLocation().getX()).isEqualTo(10.5);
        assertThat(indicator.getLocation().getZ()).isEqualTo(0.5);
        scheduler.advance(Duration.ofMillis(2999));
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(player.getHealth()).isEqualTo(14);
        assertThat(indicator.isValid()).isFalse();
        assertThat(end.getBlockAt(10, 63, 0).getType()).isEqualTo(Material.END_STONE);
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(player.getHealth()).isEqualTo(14);
    }

    @Test
    void aBombardmentFootprintCannotOverlapTheProtectedArrivalArea() throws Exception {
        config.node("arrival-x").set(16);
        config.node("arrival-y").set(64);
        config.node("arrival-radius").set(3);
        enable();
        scheduler.advance(Duration.ofSeconds(8));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(20);
    }

    @Test
    void aRefusedVisibleWarningCannotLeaveAnInvisibleDamagingAttack() throws Exception {
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void veto(org.bukkit.event.entity.EntitySpawnEvent event) {
                if (event.getEntity() instanceof org.bukkit.entity.TextDisplay) {
                    event.setCancelled(true);
                }
            }
        }, plugin);
        enable();
        scheduler.advance(Duration.ofSeconds(11));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        assertThat(anchors()).hasSize(4);
        assertThat(hit(dragon).isCancelled()).isTrue();
    }

    @Test
    void movingOutOfTheWarnedCircleAvoidsDamageWithoutMovingTheImpactToTheNewPosition() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(8));
        var warning = end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class).iterator().next();
        var teammate = server.addPlayer();
        teammate.teleport(player.getLocation());
        player.teleport(new Location(end, 20.5, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(teammate.getHealth()).isEqualTo(14);
        assertThat(warning.isValid()).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"arrival", "world", "quit", "death", "creative", "spectator"})
    void anInvalidatedTargetCannotLeaveAStaleAttackOnRemainingPlayers(String departure) throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(8));
        var warning = end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class).iterator().next();
        var teammate = server.addPlayer();
        teammate.teleport(player.getLocation());
        switch (departure) {
            case "arrival" -> player.teleport(new Location(end, 100, 50, 0));
            case "world" -> player.teleport(new Location(server.addSimpleWorld("other"), 10, 64, 0));
            case "quit" -> player.kick();
            case "death" -> player.setHealth(0);
            case "creative" -> player.setGameMode(org.bukkit.GameMode.CREATIVE);
            case "spectator" -> player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            default -> throw new AssertionError(departure);
        }
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(warning.isValid()).isFalse();
        assertThat(teammate.getHealth()).isEqualTo(20);
        assertThat(player.getHealth()).isEqualTo(departure.equals("death") ? 0 : 20);
    }

    @Test
    void theLastAnchorCancelsTheWarnedOpeningAttackImmediatelyAndHuntReceivesNoDelayedDamage() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(8));
        var warning = end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class).iterator().next();
        defenders().forEach(this::defeat);
        var objectives = anchors();
        objectives.forEach(this::hit);
        assertThat(warning.isValid()).isFalse();
        assertThat(hit(dragon).isCancelled()).isFalse();
        // Hunt now has its own attacks; only the pending opening impact must be cancelled here.
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        assertThat(drainMessages(player)).isEmpty();
    }

    @Test
    void unsafeVoidBoundPlacementIsOmittedButAUsableWalkingCorridorAllowsAnEscape() throws Exception {
        // A tiny isolated patch supports the target but no route out of a four-block impact circle.
        for (int x = 5; x <= 16; x++) {
            for (int z = -6; z <= 6; z++) {
                if (x < 9 || x > 11 || z < -1 || z > 1) {
                    end.getBlockAt(x, 63, z).setType(Material.AIR);
                }
            }
        }
        enable();
        scheduler.advance(Duration.ofSeconds(11));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        assertThat(player.getHealth()).isEqualTo(20);
        // Restore only a westward corridor. Safety must use actual footing, not an empty arena sector.
        for (int x = 4; x <= 11; x++) {
            for (int z = -1; z <= 1; z++) {
                end.getBlockAt(x, 63, z).setType(Material.END_STONE);
            }
        }
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).hasSize(1);
        player.teleport(new Location(end, 4.5, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(20);
    }

    @Test
    void combatCancellationIsHonoredWithoutApplyingCustomDamageTwice() throws Exception {
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void protect(EntityDamageEvent event) {
                if (event.getEntity() instanceof org.bukkit.entity.Player) {
                    event.setCancelled(true);
                }
            }
        }, plugin);
        enable();
        scheduler.advance(Duration.ofSeconds(11));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
    }

    @Test
    void anUnloadedWarningIsCancelledInsteadOfResolvingAnInvisibleAttack() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(8));
        end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class).iterator().next().remove();
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(20);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"victory", "disable", "unload"})
    void terminatingDuringAWarningRemovesOnlyOwnedEffectsAndNeverDealsDelayedDamage(String termination)
            throws Exception {
        var unrelated = end.spawn(new Location(end, 50, 64, 0), org.bukkit.entity.TextDisplay.class);
        enable();
        scheduler.advance(Duration.ofSeconds(8));
        var warning = end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class).stream()
                .filter(entity -> entity != unrelated).findFirst().orElseThrow();
        switch (termination) {
            case "victory" -> {
                for (int stage = 0; stage < 2; stage++) {
                    defenders().forEach(this::defeat);
                    anchors().forEach(this::hit);
                    if (stage == 0) {
                        dragon.setHealth(310);
                        var crossing = hit(dragon);
                        assertThat(crossing.isCancelled()).isFalse();
                        dragon.setHealth(dragon.getHealth() - crossing.getFinalDamage());
                        scheduler.advance(Duration.ZERO);
                    }
                }
                server.getPluginManager()
                        .callEvent(new EntityDeathEvent(dragon, mock(DamageSource.class), List.of(), 12000));
            }
            case "disable" -> module.disable();
            case "unload" -> server.getPluginManager().callEvent(new org.bukkit.event.world.WorldUnloadEvent(end));
            default -> throw new AssertionError(termination);
        }
        assertThat(warning.isValid()).isFalse();
        assertThat(anchors()).isEmpty();
        assertThat(defenders()).isEmpty();
        scheduler.advance(Duration.ofSeconds(120));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).containsExactly(unrelated);
        assertThat(dragon.isValid()).isTrue();
    }

    @Test
    void serializedRapidBombardmentsAndRepeatedResetsStayBoundedAndRestoreFourFreshGates() throws Exception {
        config.node("bombardment-warning-seconds").set(1);
        config.node("bombardment-cadence-seconds").set(2);
        config.node("bombardment-radius").set(1);
        config.node("bombardment-damage").set(0);
        enable();
        for (int attempt = 0; attempt < 8; attempt++) {
            var objectives = anchors();
            assertThat(objectives).hasSize(4).allMatch(anchor -> !anchor.isGlowing());
            assertThat(defenders()).hasSize(16);
            scheduler.advance(Duration.ofSeconds(1));
            for (int attack = 0; attack < 12; attack++) {
                scheduler.advance(Duration.ofSeconds(1));
                assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).hasSize(1);
                scheduler.advance(Duration.ofSeconds(1));
                assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
            }
            // Carry partial progress into reset; a cleared team's gate must never carry into the retry.
            var first = objectives.getFirst();
            defenders().stream().filter(defender -> defender.getLocation().distance(first.getLocation()) < 5)
                    .forEach(this::defeat);
            hit(first);
            player.teleport(new Location(end, 100, 50, 0));
            scheduler.advance(Duration.ofSeconds(60));
            assertThat(anchors()).isEmpty();
            assertThat(defenders()).isEmpty();
            assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
            assertThat(objectives).noneMatch(Entity::isValid);
            player.teleport(new Location(end, 10.5, 64, 0.5));
            scheduler.advance(Duration.ofSeconds(1));
            assertThat(anchors()).hasSize(4).doesNotContainAnyElementsOf(objectives);
        }
        assertThat(player.getHealth()).isEqualTo(20);
    }

    @Test
    void anOrdinaryReloadCannotChangeAnAlreadyWarnedAttackAndNewTuningAppliesAfterReset() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(8));
        var next = config.copy();
        next.node("bombardment-warning-seconds").set(2);
        next.node("bombardment-cadence-seconds").set(4);
        next.node("bombardment-radius").set(2);
        next.node("bombardment-damage").set(3);
        module.onConfigReload(next);
        scheduler.advance(Duration.ofMillis(2999));
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(player.getHealth()).isEqualTo(14);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(60));
        player.setHealth(20);
        player.teleport(new Location(end, 10.5, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(1));
        scheduler.advance(Duration.ofSeconds(6));
        assertThat(player.getHealth()).isEqualTo(17);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "bombardment-warning-seconds,0", "bombardment-cadence-seconds,0", "bombardment-damage,NaN",
            "bombardment-radius,Infinity", "bombardment-radius,13", "bombardment-warning-seconds,1",
            "bombardment-cadence-seconds,3"
    })
    void invalidBombardmentReloadsAreRejectedWithoutDiscardingThePlayableOpening(String key, double value)
            throws Exception {
        enable();
        var invalid = config.copy();
        invalid.node(key).set(value);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> module.onConfigReload(invalid))
                .isInstanceOf(RuntimeException.class);
        assertThat(anchors()).hasSize(4);
        scheduler.advance(Duration.ofSeconds(11));
        assertThat(player.getHealth()).isEqualTo(14);
    }

    @Test
    void disableFromACombatEventStopsTheRestOfTheImpactRatherThanDamagingAfterCleanup() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(8));
        var teammate = server.addPlayer();
        teammate.teleport(player.getLocation());
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void stop(EntityDamageEvent event) {
                if (event.getEntity() instanceof org.bukkit.entity.Player) {
                    event.setCancelled(true);
                    module.disable();
                }
            }
        }, plugin);
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(teammate.getHealth()).isEqualTo(20);
        assertThat(anchors()).isEmpty();
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
    }

    @Test
    void existingDragonBreathDangerIsNotCoveredByANewBombardmentAndItsExpiryFreesTheRoute() throws Exception {
        var breath = end.spawn(player.getLocation(), org.bukkit.entity.AreaEffectCloud.class);
        breath.setParticle(org.bukkit.Particle.DRAGON_BREATH);
        breath.setRadius(3);
        enable();
        scheduler.advance(Duration.ofSeconds(11));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        assertThat(player.getHealth()).isEqualTo(20);
        breath.remove();
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).hasSize(1);
        player.teleport(new Location(end, 20.5, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(20);
    }

    @Test
    void warningsKeepWorldCountdownAndDistinctNativeSoundsWithoutChatOrActionBars() throws Exception {
        var german = server.addPlayer("Deutsch");
        german.teleport(player.getLocation());
        enable();
        scheduler.advance(Duration.ofSeconds(8));
        assertThat(drainActionBars(player)).isEmpty();
        assertThat(drainActionBars(german)).isEmpty();
        var countdown = end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class).iterator().next();
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertThat(plain.serialize(countdown.text())).contains("3");
        assertThat(player.getHeardSounds()).anyMatch(sound -> sound.getSound().contains("ender_dragon.growl"));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHeardSounds()).anyMatch(sound -> sound.getSound().contains("generic.explode"));
        assertThat(drainMessages(player)).isEmpty();
        assertThat(drainMessages(german)).isEmpty();
    }

    private List<String> drainActionBars(PlayerMock recipient) {
        var bars = new java.util.ArrayList<String>();
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        net.kyori.adventure.text.Component bar;
        while ((bar = recipient.nextActionBar()) != null) {
            bars.add(plain.serialize(bar));
        }
        return bars;
    }

    @Test
    void onlyEligibleLivingCombatantsAreSelectedAndTheWarnedFootprintBoundsDamage() throws Exception {
        var protectedPlayer = server.addPlayer();
        protectedPlayer.teleport(new Location(end, 100, 50, 0));
        var creative = server.addPlayer();
        creative.teleport(player.getLocation());
        creative.setGameMode(org.bukkit.GameMode.CREATIVE);
        var spectator = server.addPlayer();
        spectator.teleport(player.getLocation());
        spectator.setGameMode(org.bukkit.GameMode.SPECTATOR);
        var outside = server.addPlayer();
        outside.teleport(new Location(end, 200, 64, 0));
        var dead = server.addPlayer();
        dead.teleport(player.getLocation());
        dead.setHealth(0);
        enable();
        scheduler.advance(Duration.ofSeconds(8));
        var indicator = end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class).iterator().next();
        assertThat(indicator.getLocation().getX()).isEqualTo(10.5);
        assertThat(indicator.getLocation().getZ()).isEqualTo(0.5);
        var edge = server.addPlayer();
        edge.teleport(new Location(end, 14.5, 64, 0.5));
        var beyond = server.addPlayer();
        beyond.teleport(new Location(end, 14.51, 64, 0.5));
        var above = server.addPlayer();
        above.teleport(new Location(end, 10.5, 68, 0.5));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(14);
        assertThat(edge.getHealth()).isEqualTo(14);
        assertThat(beyond.getHealth()).isEqualTo(20);
        assertThat(above.getHealth()).isEqualTo(20);
        assertThat(protectedPlayer.getHealth()).isEqualTo(20);
        assertThat(creative.getHealth()).isEqualTo(20);
        assertThat(spectator.getHealth()).isEqualTo(20);
        assertThat(outside.getHealth()).isEqualTo(20);
        assertThat(dead.getHealth()).isZero();
    }

    @Test
    void threeUsableObjectivesCannotStartAPartialOrImpossibleFourAnchorEncounter() throws Exception {
        for (int x = -12; x <= 12; x++) {
            for (int z = -36; z <= -12; z++) {
                end.getBlockAt(x, 63, z).setType(Material.AIR);
            }
        }
        enable();
        scheduler.advance(Duration.ofSeconds(11));
        assertThat(anchors()).isEmpty();
        assertThat(defenders()).isEmpty();
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        assertThat(dragon.getHealth()).isEqualTo(200);
    }

    @Test
    void disableDuringWarningSpawnCannotLeaveNewEffectsAfterCleanup() throws Exception {
        enable();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void stop(org.bukkit.event.entity.EntitySpawnEvent event) {
                if (event.getEntity() instanceof org.bukkit.entity.TextDisplay) {
                    module.disable();
                }
            }
        }, plugin);
        scheduler.advance(Duration.ofSeconds(11));
        assertThat(anchors()).isEmpty();
        assertThat(defenders()).isEmpty();
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        assertThat(player.getHealth()).isEqualTo(20);
    }

    @Test
    void pointSizedDiagonalGapsBetweenWallsAreNotTreatedAsPlayerSizedEscapeRoutes() throws Exception {
        for (int x = 1; x <= 19; x++) {
            for (int z = -10; z <= 10; z++) {
                end.getBlockAt(x, 64, z).setType(Material.OBSIDIAN);
                end.getBlockAt(x, 65, z).setType(Material.OBSIDIAN);
            }
        }
        // Empty diagonal cells touch at corners; a point fits but a normal player's body does not.
        for (int step = 0; step <= 8; step++) {
            end.getBlockAt(10 - step, 64, -step).setType(Material.AIR);
            end.getBlockAt(10 - step, 65, -step).setType(Material.AIR);
        }
        enable();
        scheduler.advance(Duration.ofSeconds(8));
        assertThat(anchors()).hasSize(4);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(20);
    }
}
