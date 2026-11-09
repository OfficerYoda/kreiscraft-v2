package de.kreiscraft.modules.shatteredcrown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.bukkit.entity.Entity;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

/** Finale policy through module lifecycle, public combat events and observable world effects. */
class FinalEncounterTest {
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
    private List<Location> openingLocations;

    @BeforeEach
    void setUp() throws Exception {
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
                // MockBukkit lacks only the billboard setter.
            }

            @Override
            public void bombardmentDamage(org.bukkit.entity.Player recipient, EnderDragon source, double amount) {
                new org.mockbukkit.mockbukkit.simulate.entity.LivingEntitySimulation((PlayerMock) recipient)
                        .simulateDamage(amount, DamageSource.builder(DamageType.DRAGON_BREATH)
                                .withCausingEntity(source).build());
            }
        });
        config = BasicConfigurationNode.root();
        config.node("enabled").set(true);
        config.node("world-key").set(end.getKey().toString());
    }

    @AfterEach
    void tearDown() {
        module.disable();
        MockBukkit.unmock();
    }

    private void opening() throws Exception {
        module.enable(new ModuleContext(plugin, config,
                new ConfigurateMessageService(data, getClass().getClassLoader(), "en", server,
                        recipient -> recipient.getName().equals("Deutsch") ? java.util.Optional.of("de")
                                : java.util.Optional.empty()),
                new ServiceRegistry(), scheduler, mock(CommandRegistrar.class), new EventBus(plugin)));
        scheduler.advance(Duration.ZERO);
        openingLocations = crystals().stream().map(Entity::getLocation).toList();
    }

    private void hunt() throws Exception {
        opening();
        clearAnchors();
    }

    private List<EnderCrystal> crystals() {
        return List.copyOf(end.getEntitiesByClass(EnderCrystal.class));
    }

    private void clearAnchors() {
        List.copyOf(end.getEntitiesByClass(Zombie.class)).forEach(this::defeat);
        crystals().forEach(target -> damage(target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10));
    }

    private void defeat(Zombie defender) {
        defender.setHealth(0);
        server.getPluginManager().callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
    }

    private EntityDamageEvent damage(Entity target, EntityDamageEvent.DamageCause cause, double amount) {
        var event = new EntityDamageByEntityEvent(player, target, cause, mock(DamageSource.class), amount);
        server.getPluginManager().callEvent(event);
        // Dispatching a public event does not apply native damage in MockBukkit; no dragon AI exists.
        if (!event.isCancelled() && (target == dragon
                || target instanceof org.bukkit.entity.ComplexEntityPart part && part.getParent() == dragon)) {
            dragon.setHealth(Math.max(0, dragon.getHealth() - event.getFinalDamage()));
        }
        return event;
    }

    @ParameterizedTest
    @ValueSource(doubles = {700, 701, 1000, 100000})
    void thresholdCrossingIncludingLethalDamageReactivatesOnlyTwoClearedSitesBeforeDeath(double amount)
            throws Exception {
        hunt();
        var hit = damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, amount);
        assertThat(dragon.getHealth()).isEqualTo(300);
        assertThat(hit.getFinalDamage()).isEqualTo(700);
        assertThat(crystals()).hasSize(2);
        assertThat(crystals().stream().map(Entity::getLocation)).isSubsetOf(openingLocations);
        assertThat(end.getEntitiesByClass(Zombie.class)).hasSize(8);
        crystals().forEach(anchor -> {
            damage(anchor, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10);
            assertThat(anchor.isValid()).isTrue();
        });
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.PROJECTILE, 100000).isCancelled()).isTrue();
        assertThat(dragon.getHealth()).isEqualTo(300);
    }

    @Test
    void finalFinishUsesEarlierFamiliarWarningsAndThreeSecondPools() throws Exception {
        config.node("lane-cadence-seconds").set(120);
        config.node("sweep-cadence-seconds").set(120);
        config.node("prey-cadence-seconds").set(20);
        config.node("pool-damage").set(0);
        hunt();
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000);
        clearAnchors();
        scheduler.advance(Duration.ofMillis(13999));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofMillis(1));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        scheduler.advance(Duration.ofSeconds(4));
        var pool = end.getEntitiesByClass(TextDisplay.class).iterator().next();
        assertThat(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(pool.text())).contains("3");
        scheduler.advance(Duration.ofMillis(2999));
        assertThat(pool.isValid()).isTrue();
        scheduler.advance(Duration.ofMillis(1));
        assertThat(pool.isValid()).isFalse();
    }

    @Test
    void simultaneousExplosionsShareARollingBudgetAcrossDragonPartsWithoutRestrictingOtherDamage() throws Exception {
        hunt();
        var part = mock(org.bukkit.entity.ComplexEntityPart.class);
        when(part.getParent()).thenReturn(dragon);
        damage(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 30);
        var second = damage(part, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, 30);
        assertThat(second.getFinalDamage()).isEqualTo(10);
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 30).isCancelled()).isTrue();
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10).isCancelled()).isFalse();
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 30).isCancelled()).isTrue();
        scheduler.advance(Duration.ofMillis(1050));
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, 30).getFinalDamage()).isEqualTo(30);
        var unrelated = end.spawn(new Location(end, 4, 64, 4), Zombie.class);
        assertThat(damage(unrelated, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 500).getFinalDamage())
                .isEqualTo(500);
    }

    @Test
    void prematureNativeDeathCannotAwardRewardsButClearedFinaleLeavesNormalCompletionUntouched() throws Exception {
        hunt();
        var premature = new EntityDeathEvent(dragon, mock(DamageSource.class), List.of(), 12000);
        server.getPluginManager().callEvent(premature);
        assertThat(premature.isCancelled()).isTrue();
        assertThat(crystals()).hasSize(2);
        clearAnchors();
        var loot = new org.bukkit.inventory.ItemStack(Material.DRAGON_EGG);
        var victory =
                new EntityDeathEvent(dragon, mock(DamageSource.class), new java.util.ArrayList<>(List.of(loot)), 12000);
        server.getPluginManager().callEvent(victory);
        assertThat(victory.isCancelled()).isFalse();
        assertThat(victory.getDroppedExp()).isEqualTo(12000);
        assertThat(victory.getDrops()).containsExactly(loot);
        scheduler.advance(Duration.ofSeconds(65));
        assertThat(crystals()).isEmpty();
        assertThat(end.getEntitiesByClass(Zombie.class)).isEmpty();
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(dragon.isValid()).isTrue();
    }

    @Test
    void refusedFinalAnchorAbortsToRetryableNativeBattleWithoutPartialArtifactsOrLethalDamage() throws Exception {
        hunt();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void refuse(org.bukkit.event.entity.EntitySpawnEvent event) {
                if (event.getEntity() instanceof EnderCrystal) {
                    event.setCancelled(true);
                }
            }
        }, plugin);
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000).isCancelled()).isTrue();
        assertThat(dragon.getHealth()).isEqualTo(200);
        assertThat(crystals()).isEmpty();
        assertThat(end.getEntitiesByClass(Zombie.class)).isEmpty();
    }

    @Test
    void budgetCannotRefreshEarlyForAHalfTickHitAtTheSlidingWindowEdge() throws Exception {
        hunt();
        scheduler.advance(Duration.ofMillis(25));
        damage(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 40);
        scheduler.advance(Duration.ofMillis(1975));
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 40).isCancelled()).isTrue();
        scheduler.advance(Duration.ofMillis(50));
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 40).isCancelled()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void finalWavesAreIndependentInEitherOrderAndHealingAndDuplicateCallbacksCannotReplayThem(boolean reverse)
            throws Exception {
        hunt();
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 700);
        var pair = crystals();
        var first = pair.get(reverse ? 1 : 0);
        var second = pair.get(reverse ? 0 : 1);
        var firstWave = List.copyOf(end.getEntitiesByClass(Zombie.class)).stream()
                .filter(defender -> defender.getLocation().distanceSquared(first.getLocation()) < 9).toList();
        assertThat(firstWave).hasSize(4);
        firstWave.forEach(defender -> {
            defeat(defender);
            server.getPluginManager().callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
        });
        assertThat(first.isGlowing()).isTrue();
        assertThat(second.isGlowing()).isFalse();
        damage(first, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10);
        damage(first, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10);
        assertThat(crystals()).containsExactly(second);
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.MAGIC, 10000).isCancelled()).isTrue();
        clearAnchors();
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.PROJECTILE, 10).isCancelled()).isFalse();
        dragon.setHealth(1000); // An allowed non-crystal heal must not grant a second interlude.
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 701);
        assertThat(dragon.getHealth()).isEqualTo(299);
        assertThat(crystals()).isEmpty();
        assertThat(end.getEntitiesByClass(Zombie.class)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"ENTITY_ATTACK", "PROJECTILE", "BLOCK_EXPLOSION", "ENTITY_EXPLOSION", "MAGIC", "FIRE", "VOID"})
    void everyManagedDamageRouteAndMultipartDuplicateHonorsTheGate(String route) throws Exception {
        hunt();
        dragon.setHealth(310);
        var part = mock(org.bukkit.entity.ComplexEntityPart.class);
        when(part.getParent()).thenReturn(dragon);
        var cause = EntityDamageEvent.DamageCause.valueOf(route);
        var first = damage(part, cause, 10000);
        assertThat(first.getFinalDamage()).isEqualTo(10);
        assertThat(dragon.getHealth()).isEqualTo(300);
        assertThat(crystals()).hasSize(2);
        assertThat(damage(part, cause, 10000).isCancelled()).isTrue();
        assertThat(damage(dragon, cause, 10000).isCancelled()).isTrue();
        assertThat(crystals()).hasSize(2);
        clearAnchors();
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000).isCancelled()).isFalse();
        assertThat(dragon.getHealth()).isZero();
    }

    @Test
    void justAboveThresholdDoesNotEnterAndCancelledDamageOrCrystalHealingCannotAdvanceIt() throws Exception {
        hunt();
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 699);
        assertThat(dragon.getHealth()).isEqualTo(301);
        assertThat(crystals()).isEmpty();
        var cancelled = new EntityDamageByEntityEvent(player, dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                mock(DamageSource.class), 10000);
        cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        assertThat(crystals()).isEmpty();
        var healing = new org.bukkit.event.entity.EntityRegainHealthEvent(dragon, 700,
                org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.ENDER_CRYSTAL);
        server.getPluginManager().callEvent(healing);
        assertThat(healing.isCancelled()).isTrue();
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1);
        assertThat(crystals()).hasSize(2);
    }

    @Test
    void laterCombatCancellationReleasesExplosionReservationsWithoutAffectingOtherRoutes() throws Exception {
        hunt();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
            public void cancel(EntityDamageEvent event) {
                if (event.getDamage() == 30) {
                    event.setCancelled(true);
                }
            }
        }, plugin);
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 30).isCancelled()).isTrue();
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, 40).getFinalDamage()).isEqualTo(40);
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 40).isCancelled()).isTrue();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"opening,reset", "hunt,reset", "protected,reset", "exposed,reset",
            "one,reset",
            "warning,reset", "active,reset", "landed,reset", "protected,unload", "exposed,unload", "one,unload",
            "warning,unload", "active,unload", "landed,unload", "protected,disable", "exposed,disable", "one,disable",
            "warning,disable", "active,disable", "landed,disable", "prey,reset", "pool,reset", "sweep,reset",
            "prey,unload", "pool,unload", "sweep,unload", "prey,disable", "pool,disable", "sweep,disable",
            "warning,victory", "active,victory", "landed,victory", "prey,victory", "pool,victory", "sweep,victory"})
    void everyFinaleStageCleansUpAndRetryResetsGateDamageBudgetAndOwnership(String stage, String termination)
            throws Exception {
        config.node("lane-damage").set(0);
        config.node("pool-damage").set(0);
        config.node("sweep-damage").set(0);
        config.node("bombardment-damage").set(0);
        if (List.of("prey", "pool", "sweep").contains(stage)) {
            config.node("lane-cadence-seconds").set(120);
            config.node("prey-cadence-seconds").set(stage.equals("sweep") ? 120 : 20);
            config.node("sweep-cadence-seconds").set(stage.equals("sweep") ? 25 : 120);
            player.teleport(new Location(end, 0.5, 64, 10.5));
        }
        opening();
        var unrelated = end.spawn(new Location(end, 35, 64, 35), TextDisplay.class);
        if (!stage.equals("opening")) {
            clearAnchors();
        }
        if (!stage.equals("opening") && !stage.equals("hunt")) {
            damage(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 40);
            damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000);
            if (!stage.equals("protected")) {
                List.copyOf(end.getEntitiesByClass(Zombie.class)).forEach(this::defeat);
            }
            if (stage.equals("one")) {
                damage(crystals().getFirst(), EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10);
            } else if (List.of("warning", "active", "landed", "prey", "pool", "sweep").contains(stage)) {
                clearAnchors();
                scheduler.advance(Duration.ofMillis(switch (stage) {
                    case "warning" -> 7000;
                    case "active" -> 10000;
                    case "prey" -> 14000;
                    case "pool" -> 18000;
                    case "sweep" -> 17500;
                    default -> 21000;
                }));
                if (stage.equals("landed")) {
                    dragon.teleport(new Location(end, 0, 67, 0));
                    dragon.setPhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
                    scheduler.advance(Duration.ofMillis(250));
                }
            }
        }
        if (List.of("warning", "active", "prey", "pool", "sweep").contains(stage)) {
            assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(2); // One real hazard plus unrelated display.
        } else if (stage.equals("landed")) {
            assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        }
        if (termination.equals("reset")) {
            player.teleport(new Location(end, 100, 50, 0));
            scheduler.advance(Duration.ofSeconds(60));
        } else if (termination.equals("unload")) {
            server.getPluginManager().callEvent(new org.bukkit.event.world.WorldUnloadEvent(end));
        } else if (termination.equals("victory")) {
            var victory = new EntityDeathEvent(dragon, mock(DamageSource.class), List.of(), 12000);
            server.getPluginManager().callEvent(victory);
            assertThat(victory.isCancelled()).isFalse();
            assertThat(victory.getDroppedExp()).isEqualTo(12000);
        } else {
            module.disable();
        }
        scheduler.advance(Duration.ofSeconds(65));
        assertThat(crystals()).isEmpty();
        assertThat(end.getEntitiesByClass(Zombie.class)).isEmpty();
        assertThat(end.getEntitiesByClass(TextDisplay.class)).containsExactly(unrelated);
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(dragon.isValid()).isTrue();
        player.teleport(new Location(end, 10.5, 64, 0.5));
        if (termination.equals("disable")) {
            opening();
        } else {
            if (termination.equals("victory")) {
                dragon.remove();
                dragon = end.spawn(new Location(end, 0, 80, 0), EnderDragon.class);
                dragon.setPodium(new Location(end, 0, 64, 0));
                dragon.getDragonBattle().setPreviouslyKilled(true);
            }
            if (termination.equals("unload")) {
                server.getPluginManager().callEvent(new org.bukkit.event.world.WorldLoadEvent(end));
            }
            scheduler.advance(Duration.ofSeconds(1));
        }
        assertThat(crystals()).hasSize(4);
        assertThat(end.getEntitiesByClass(Zombie.class)).hasSize(16);
        assertThat(dragon.getHealth()).isEqualTo(1000);
        clearAnchors();
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 40).getFinalDamage()).isEqualTo(40);
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 40).isCancelled()).isTrue();
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000);
        assertThat(crystals()).hasSize(2);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"final-health-fraction,0", "final-health-fraction,0.51",
            "final-health-fraction,NaN",
            "final-cadence-factor,0.49", "final-cadence-factor,1", "final-cadence-factor,Infinity",
            "final-pool-lifetime-factor,0.24", "final-pool-lifetime-factor,1", "final-pool-lifetime-factor,NaN",
            "explosion-window-seconds,0", "explosion-window-seconds,5.01", "explosion-window-seconds,Infinity",
            "explosion-window-budget,0", "explosion-window-budget,201", "explosion-window-budget,NaN"})
    void invalidFinalConfigurationRejectsTheWholeReloadWithoutChangingCurrentOrNextAttempt(String key, double value)
            throws Exception {
        hunt();
        var invalid = config.copy();
        invalid.node("max-health").set(800);
        invalid.node(key).set(value);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> module.onConfigReload(invalid))
                .hasMessageContaining(key);
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 700);
        assertThat(dragon.getHealth()).isEqualTo(300);
        assertThat(crystals()).hasSize(2);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(60));
        player.teleport(new Location(end, 10.5, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(dragon.getHealth()).isEqualTo(1000);
    }

    @Test
    void validReloadAppliesThresholdBudgetAndFinalTimingOnlyToTheNextAttempt() throws Exception {
        hunt();
        var next = config.copy();
        next.node("final-health-fraction").set(0.4);
        next.node("final-cadence-factor").set(0.5);
        next.node("final-pool-lifetime-factor").set(0.25);
        next.node("explosion-window-budget").set(20);
        next.node("explosion-window-seconds").set(1);
        next.node("lane-cadence-seconds").set(120);
        next.node("sweep-cadence-seconds").set(120);
        next.node("pool-damage").set(0);
        module.onConfigReload(next);
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 40).getFinalDamage()).isEqualTo(40);
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 660);
        assertThat(dragon.getHealth()).isEqualTo(300);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(60));
        player.teleport(new Location(end, 10.5, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(1));
        clearAnchors();
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 40).getFinalDamage()).isEqualTo(20);
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000);
        assertThat(dragon.getHealth()).isEqualTo(400);
        clearAnchors();
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        scheduler.advance(Duration.ofSeconds(4));
        var pool = end.getEntitiesByClass(TextDisplay.class).iterator().next();
        scheduler.advance(Duration.ofMillis(1499));
        assertThat(pool.isValid()).isTrue();
        scheduler.advance(Duration.ofMillis(1));
        assertThat(pool.isValid()).isFalse();
    }

    @Test
    void finalReactivationCannotPlaceAnObjectiveOnPreviouslyClearedButNowMissingGround() throws Exception {
        hunt();
        end.getBlockAt(24, 63, 0).setType(Material.AIR);
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000).isCancelled()).isTrue();
        assertThat(dragon.getHealth()).isEqualTo(200);
        assertThat(crystals()).isEmpty();
        assertThat(end.getEntitiesByClass(Zombie.class)).isEmpty();
    }

    @Test
    void finalLanesStartAtSevenSecondsAndLandingAtTwentyOneStillProvidesAFullNativeArrivalMeleeWindow()
            throws Exception {
        config.node("prey-cadence-seconds").set(120);
        config.node("sweep-cadence-seconds").set(120);
        config.node("lane-damage").set(0);
        hunt();
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000);
        clearAnchors();
        scheduler.advance(Duration.ofMillis(6999));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofMillis(1));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        scheduler.advance(Duration.ofSeconds(14));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.FLY_TO_PORTAL);
        scheduler.advance(Duration.ofSeconds(5));
        dragon.teleport(new Location(end, 0, 67, 0));
        dragon.setPhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        scheduler.advance(Duration.ofMillis(250));
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10).isCancelled()).isFalse();
        var takeoff = new org.bukkit.event.entity.EnderDragonChangePhaseEvent(dragon, dragon.getPhase(),
                EnderDragon.Phase.LEAVE_PORTAL);
        server.getPluginManager().callEvent(takeoff);
        assertThat(takeoff.getNewPhase()).isEqualTo(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        scheduler.advance(Duration.ofMillis(11999));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofMillis(1));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.LEAVE_PORTAL);
    }

    @Test
    void finalSweepUsesTheSameFourSecondWarningButStartsAtSeventeenAndAHalfSeconds() throws Exception {
        config.node("prey-cadence-seconds").set(120);
        config.node("lane-cadence-seconds").set(120);
        player.teleport(new Location(end, 0.5, 64, 10.5));
        hunt();
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000);
        clearAnchors();
        scheduler.advance(Duration.ofMillis(17499));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofMillis(1));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofMillis(3999));
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(player.getHealth()).isEqualTo(14);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofMillis(250));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.FLY_TO_PORTAL);
    }

    @Test
    void finalInterludeHasFamiliarLocalizedBombardmentsAndClearTwoObjectiveShieldPresentation() throws Exception {
        var german = server.addPlayer("Deutsch");
        german.teleport(player.getLocation());
        hunt();
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000);
        assertThat(messages(player)).anyMatch(text -> text.contains("Two Anchors return once"));
        assertThat(messages(german)).anyMatch(text -> text.contains("Zwei Anker kehren einmal"));
        assertThat(bars(player))
                .anyMatch(text -> text.contains("Shatter the Last Stand") && text.contains("Anchors: 2"));
        assertThat(bars(german)).anyMatch(text -> text.contains("letzten Widerstand") && text.contains("Anker: 2"));
        scheduler.advance(Duration.ofSeconds(8));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        assertThat(bars(player)).anyMatch(text -> text.contains("Bombardment in 3.0s"));
        assertThat(bars(german)).anyMatch(text -> text.contains("Bombardierung in 3.0s"));
        player.teleport(player.getLocation().add(6, 0, 0));
        german.teleport(german.getLocation().add(6, 0, 0));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(german.getHealth()).isEqualTo(20);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        clearAnchors();
        assertThat(messages(player)).anyMatch(text -> text.contains("Both final Anchors"));
        assertThat(messages(german)).anyMatch(text -> text.contains("Beide finalen Anker"));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(bars(player)).anyMatch(text -> text.contains("Faster attacks, shorter pools"));
        assertThat(bars(german)).anyMatch(text -> text.contains("Schnellere Angriffe"));
    }

    private List<String> messages(PlayerMock recipient) {
        var texts = new java.util.ArrayList<String>();
        String text;
        while ((text = recipient.nextMessage()) != null) {
            texts.add(text);
        }
        return texts;
    }

    private List<String> bars(PlayerMock recipient) {
        var texts = new java.util.ArrayList<String>();
        net.kyori.adventure.text.Component text;
        while ((text = recipient.nextActionBar()) != null) {
            texts.add(
                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(text));
        }
        return texts;
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,12000", "true,500"})
    void fullOpeningHuntFinalPairVictoryDemoPreservesInitialAndRitualRewardPayloadsWithoutCustomRewards(
            boolean previouslyKilled, int experience) throws Exception {
        dragon.getDragonBattle().setPreviouslyKilled(previouslyKilled);
        hunt();
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 699);
        assertThat(crystals()).isEmpty();
        damage(dragon, EntityDamageEvent.DamageCause.PROJECTILE, 10000);
        assertThat(dragon.getHealth()).isEqualTo(300);
        assertThat(crystals()).hasSize(2);
        clearAnchors();
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000);
        var victory = new EntityDeathEvent(dragon, mock(DamageSource.class), new java.util.ArrayList<>(), experience);
        server.getPluginManager().callEvent(victory);
        server.getPluginManager().callEvent(victory);
        assertThat(victory.isCancelled()).isFalse();
        assertThat(victory.getDroppedExp()).isEqualTo(experience);
        assertThat(victory.getDrops()).isEmpty();
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isEqualTo(previouslyKilled);
        scheduler.advance(Duration.ofSeconds(65));
        assertThat(crystals()).isEmpty();
        assertThat(end.getEntitiesByClass(Zombie.class)).isEmpty();
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void repeatedTwentyPlayerFinalesAndFractionalExplosionSpamKeepHazardsAndSchedulerWorkBounded() throws Exception {
        scheduler = org.mockito.Mockito.spy(scheduler);
        var scheduled = new java.util.ArrayList<de.kreiscraft.core.scheduler.Scheduler.Task>();
        org.mockito.stubbing.Answer<Object> track = invocation -> {
            var task = (de.kreiscraft.core.scheduler.Scheduler.Task) invocation.callRealMethod();
            scheduled.add(task);
            return task;
        };
        org.mockito.Mockito.doAnswer(track).when(scheduler).runLater(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doAnswer(track).when(scheduler).runRepeating(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        config.node("prey-count").set(4);
        config.node("lane-damage").set(0);
        config.node("pool-damage").set(0);
        config.node("sweep-damage").set(0);
        var participants = new java.util.ArrayList<PlayerMock>(List.of(player));
        for (int i = 1; i < 20; i++) {
            var teammate = server.addPlayer();
            teammate.teleport(new Location(end, -18.5 + i % 5 * 8, 64, -18.5 + i / 5 * 8));
            participants.add(teammate);
        }
        opening();
        for (int attempt = 0; attempt < 3; attempt++) {
            clearAnchors();
            long before = scheduled.stream().filter(task -> !task.isCancelled()).count();
            for (int burst = 0; burst < 1000; burst++) {
                damage(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, 0.001);
            }
            assertThat(scheduled.stream().filter(task -> !task.isCancelled()).count()).isEqualTo(before);
            damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000);
            clearAnchors();
            for (int second = 0; second < 180; second++) {
                scheduler.advance(Duration.ofSeconds(1));
                assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSizeLessThanOrEqualTo(4);
                assertThat(scheduled.stream().filter(task -> !task.isCancelled()).count()).isLessThanOrEqualTo(21);
            }
            participants.forEach(participant -> participant.teleport(new Location(end, 100, 50, 0)));
            scheduler.advance(Duration.ofSeconds(60));
            assertThat(crystals()).isEmpty();
            assertThat(end.getEntitiesByClass(Zombie.class)).isEmpty();
            assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
            assertThat(scheduled.stream().filter(task -> !task.isCancelled()).count()).isEqualTo(1);
            participants.forEach(participant -> participant.teleport(new Location(end, 10.5, 64, 0.5)));
            scheduler.advance(Duration.ofSeconds(1));
            assertThat(crystals()).hasSize(4);
        }
    }

    @Test
    void enteringFinalGateDoesNotRestartAnAlreadyRunningContinuousAbsenceTimeout() throws Exception {
        hunt();
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(59));
        var environment = new EntityDamageEvent(dragon, EntityDamageEvent.DamageCause.FIRE,
                mock(DamageSource.class), 10000);
        server.getPluginManager().callEvent(environment);
        assertThat(crystals()).hasSize(2);
        scheduler.advance(Duration.ofMillis(999));
        assertThat(crystals()).hasSize(2);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(crystals()).isEmpty();
        assertThat(dragon.getHealth()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void synchronousFinalSpawnCallbacksCannotDealLethalDamageOrResumeAfterDisable(boolean disable) throws Exception {
        hunt();
        var nested = new java.util.ArrayList<EntityDamageEvent>();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void spawn(org.bukkit.event.entity.EntitySpawnEvent event) {
                if (event.getEntity() instanceof EnderCrystal) {
                    nested.add(damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000));
                    if (disable) {
                        module.disable();
                    }
                }
            }
        }, plugin);
        var crossing = damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000);
        assertThat(nested).isNotEmpty().allMatch(EntityDamageEvent::isCancelled);
        if (disable) {
            assertThat(crossing.isCancelled()).isTrue();
            assertThat(crystals()).isEmpty();
            assertThat(end.getEntitiesByClass(Zombie.class)).isEmpty();
            assertThat(dragon.getHealth()).isEqualTo(200);
            scheduler.advance(Duration.ofSeconds(65));
            assertThat(crystals()).isEmpty();
        } else {
            assertThat(crystals()).hasSize(2);
            assertThat(end.getEntitiesByClass(Zombie.class)).hasSize(8);
            assertThat(dragon.getHealth()).isEqualTo(300);
        }
    }

    @Test
    void finalThirtyPercentGateUsesTheEffectiveMaximumSnapshotAndPreservesNativeHealthModifiersOnAbort()
            throws Exception {
        var maximum = dragon.getAttribute(Attribute.MAX_HEALTH);
        var modifier = new org.bukkit.attribute.AttributeModifier(new org.bukkit.NamespacedKey("test", "native_health"),
                -50, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER);
        maximum.addModifier(modifier);
        hunt();
        assertThat(dragon.getHealth()).isEqualTo(950);
        var crossing = damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000);
        assertThat(crossing.getFinalDamage()).isEqualTo(665);
        assertThat(dragon.getHealth()).isEqualTo(285);
        assertThat(crystals()).hasSize(2);
        module.disable();
        assertThat(maximum.getBaseValue()).isEqualTo(200);
        assertThat(maximum.getModifiers()).containsExactly(modifier);
        assertThat(dragon.getHealth()).isEqualTo(45);
    }

    @Test
    void synchronousDefenderSpawnCallbacksCannotClearFinalAnchorsBeforeTheirWavesHaveBeenInstalled() throws Exception {
        hunt();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void spawn(org.bukkit.event.entity.EntitySpawnEvent event) {
                if (event.getEntity() instanceof Zombie) {
                    crystals().forEach(anchor -> damage(anchor, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10));
                }
            }
        }, plugin);
        damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000);
        assertThat(crystals()).hasSize(2).allMatch(anchor -> !anchor.isGlowing());
        assertThat(end.getEntitiesByClass(Zombie.class)).hasSize(8);
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10000).isCancelled()).isTrue();
        clearAnchors();
        assertThat(crystals()).isEmpty();
        assertThat(damage(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10).isCancelled()).isFalse();
    }
}
