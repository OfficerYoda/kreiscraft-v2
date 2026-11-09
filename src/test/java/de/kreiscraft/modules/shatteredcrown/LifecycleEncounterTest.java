package de.kreiscraft.modules.shatteredcrown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;

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
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
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

/** Lifecycle observations at the same approved encounter/module boundary as EncounterTest. */
class LifecycleEncounterTest {
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
    private ModuleContext context;
    private DragonBattleAccess battles;

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
        player = server.addPlayer();
        player.teleport(new Location(end, 10, 64, 0));
        scheduler = new MockScheduler();
        // Only unsupported Paper lookup/native attribute metadata is adapted, not encounter behavior.
        battles = new DragonBattleAccess() {
            @Override
            public org.bukkit.boss.DragonBattle battle(World world) {
                return world == end ? dragon.getDragonBattle() : null;
            }

            @Override
            public void preventDespawn(Mob defender) {
                defender.registerAttribute(Attribute.ATTACK_DAMAGE);
                defender.registerAttribute(Attribute.SPAWN_REINFORCEMENTS);
            }
        };
        module = new ShatteredCrownModule(battles);
        config = BasicConfigurationNode.root();
        config.node("world-key").set(end.getKey().toString());
        config.node("enabled").set(true);
        context = new ModuleContext(plugin, config,
                new ConfigurateMessageService(data, getClass().getClassLoader(), "en", server),
                new ServiceRegistry(), scheduler, mock(CommandRegistrar.class), new EventBus(plugin));
    }

    @AfterEach
    void tearDown() {
        module.disable();
        MockBukkit.unmock();
    }

    @Test
    void removingTheSectionOnDiskReloadDisablesTheAttemptAndReturnCannotRestartIt() throws Exception {
        var yaml = new de.kreiscraft.core.config.YamlConfigService(data, getClass().getClassLoader(),
                ShatteredCrownConfig::validateCandidate);
        java.nio.file.Files.writeString(data.resolve("config.yml"), """
                modules:
                  shattered-crown:
                    enabled: true
                    world-key: %s
                    max-health: 800
                """.formatted(end.getKey()));
        yaml.reload();
        module.enable(new ModuleContext(plugin, yaml.module("shattered-crown"), context.messages(),
                context.services(), scheduler, context.commands(), context.events()));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).hasSize(4);
        assertThat(dragon.getHealth()).isEqualTo(800);

        java.nio.file.Files.writeString(data.resolve("config.yml"), "modules: {}\n");
        yaml.reload();
        assertThat(ShatteredCrownConfig.load(yaml.module("shattered-crown")).maxHealth()).isEqualTo(1000);
        module.onConfigReload(yaml.module("shattered-crown"));
        assertThat(crystals()).isEmpty();
        assertThat(end.getEntitiesByClass(Zombie.class)).isEmpty();
        assertThat(dragon.getHealth()).isEqualTo(200);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(60));
        player.teleport(new Location(end, 10, 64, 0));
        scheduler.advance(Duration.ofSeconds(35));
        assertThat(crystals()).isEmpty();
        assertThat(dragon.getHealth()).isEqualTo(200);
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isFalse();
        assertThat(yaml.module("shattered-crown").virtual()).isTrue();
    }

    @Test
    void nativeNegativeMaximumHealthModifierSurvivesActivationResetAndDisable() {
        // MockBukkit silently clamps setHealth; supported CraftLivingEntity rejects above-effective-max values.
        // Adapt that one Paper contract, leaving attributes, events and encounter behavior real.
        dragon = spy(dragon);
        doAnswer(invocation -> {
            double health = invocation.getArgument(0);
            if (health < 0 || health > dragon.getAttribute(Attribute.MAX_HEALTH).getValue()) {
                throw new IllegalArgumentException("Health exceeds the effective maximum");
            }
            return invocation.callRealMethod();
        }).when(dragon).setHealth(org.mockito.ArgumentMatchers.anyDouble());
        var nativeBattle = spy(dragon.getDragonBattle());
        org.mockito.Mockito.doReturn(dragon).when(nativeBattle).getEnderDragon();
        module = new ShatteredCrownModule(new DragonBattleAccess() {
            @Override
            public org.bukkit.boss.DragonBattle battle(World world) {
                return world == end ? nativeBattle : null;
            }

            @Override
            public void preventDespawn(Mob defender) {
                battles.preventDespawn(defender);
            }
        });
        var maximum = dragon.getAttribute(Attribute.MAX_HEALTH);
        var modifier = new org.bukkit.attribute.AttributeModifier(new NamespacedKey("test", "native_health"),
                -50, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER);
        maximum.addModifier(modifier);
        dragon.setHealth(150);
        module.enable(context);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).hasSize(4);
        assertThat(maximum.getBaseValue()).isEqualTo(1000);
        assertThat(maximum.getValue()).isEqualTo(950);
        assertThat(dragon.getHealth()).isEqualTo(950);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(60));
        assertThat(crystals()).isEmpty();
        assertThat(maximum.getBaseValue()).isEqualTo(200);
        assertThat(dragon.getHealth()).isEqualTo(150);
        player.teleport(new Location(end, 10, 64, 0));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).hasSize(4);
        module.disable();
        assertThat(crystals()).isEmpty();
        assertThat(maximum.getModifiers()).containsExactly(modifier);
        assertThat(maximum.getValue()).isEqualTo(150);
        assertThat(dragon.getHealth()).isEqualTo(150);
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void discoveryDoesNotEnhanceNativeDyingFlightForInitialOrRitualDragons(boolean previouslyKilled) {
        dragon.getDragonBattle().setPreviouslyKilled(previouslyKilled);
        dragon.setPhase(EnderDragon.Phase.DYING);
        dragon.setHealth(1); // Native flying lethal hits enter DYING before actual zero-health death.
        module.enable(context);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntitySpawnEvent(dragon));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(crystals()).isEmpty();
        assertThat(end.getEntitiesByClass(Zombie.class)).isEmpty();
        assertThat(dragon.getHealth()).isEqualTo(1);
        assertThat(dragon.getAttribute(Attribute.MAX_HEALTH).getBaseValue()).isEqualTo(200);
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.DYING);
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isEqualTo(previouslyKilled);
        assertThat(dragon.getPersistentDataContainer().has(new NamespacedKey("kreiscraft", "crown_original_health")))
                .isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void restartRecoversManagedHealthFromPreDeathDyingFlightWithoutPhaseOwnership(boolean previouslyKilled)
            throws Exception {
        dragon.getDragonBattle().setPreviouslyKilled(previouslyKilled);
        dragon.getPersistentDataContainer().set(new NamespacedKey("kreiscraft", "crown_original_health"),
                PersistentDataType.DOUBLE, 200.0);
        dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);
        dragon.setHealth(1);
        dragon.setPhase(EnderDragon.Phase.DYING);
        config.node("enabled").set(false);
        module.enable(context);
        assertThat(dragon.getHealth()).isEqualTo(200);
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        assertThat(dragon.getPersistentDataContainer().has(new NamespacedKey("kreiscraft", "crown_original_health")))
                .isFalse();
        dragon.setHealth(180);
        server.getPluginManager().callEvent(new org.bukkit.event.world.EntitiesLoadEvent(end.getChunkAt(0, 0),
                List.of(dragon)));
        scheduler.advance(Duration.ofSeconds(120));
        assertThat(dragon.getHealth()).isEqualTo(180); // No repeated recovery/refill after metadata is consumed.
        assertThat(crystals()).isEmpty();
        assertThat(end.getEntitiesByClass(EnderDragon.class)).containsExactly(dragon);
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isEqualTo(previouslyKilled);
    }

    @Test
    void recoveryNeverRevivesAZeroHealthNativeDeathEvenWithBothOwnershipMarkers() throws Exception {
        config.node("enabled").set(false);
        module.enable(context);
        dragon.getDragonBattle().setPreviouslyKilled(true);
        dragon.getPersistentDataContainer().set(new NamespacedKey("kreiscraft", "crown_original_health"),
                PersistentDataType.DOUBLE, 200.0);
        dragon.getPersistentDataContainer().set(new NamespacedKey("kreiscraft", "crown_original_phase"),
                PersistentDataType.STRING, "CIRCLING");
        dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);
        dragon.setPhase(EnderDragon.Phase.DYING);
        dragon.setHealth(0);
        server.getPluginManager().callEvent(new org.bukkit.event.world.EntitiesLoadEvent(end.getChunkAt(0, 0),
                List.of(dragon)));
        assertThat(dragon.isDead()).isTrue();
        assertThat(dragon.getHealth()).isZero();
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.DYING);
        assertThat(dragon.getAttribute(Attribute.MAX_HEALTH).getBaseValue()).isEqualTo(1000);
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isTrue();
        assertThat(crystals()).isEmpty();
    }

    @Test
    void disabledStartupRecoversOwnedArtifactsAndDragonButNeverStartsCombat() throws Exception {
        var stale = end.spawn(player.getLocation(), EnderCrystal.class);
        mark(stale, "anchor");
        var ordinary = end.spawn(player.getLocation(), EnderCrystal.class);
        dragon.getPersistentDataContainer().set(new NamespacedKey("kreiscraft", "crown_original_health"),
                PersistentDataType.DOUBLE, 200.0);
        dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);
        dragon.setHealth(12);
        config.node("enabled").set(false);
        module.enable(context);
        scheduler.advance(Duration.ofSeconds(120));
        assertThat(crystals()).containsExactly(ordinary);
        assertThat(dragon.getHealth()).isEqualTo(200);
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isFalse();
        assertThat(end.getEntitiesByClass(EnderDragon.class)).containsExactly(dragon);
    }

    @Test
    void repeatedEnableAndDiscoveryKeepOneAttemptAndDisableCancelsEveryRegistration() {
        module.enable(context);
        scheduler.advance(Duration.ofSeconds(1));
        var first = crystals();
        assertThat(first).isNotEmpty();
        var firstDefenders = List.copyOf(end.getEntitiesByClass(Zombie.class));
        module.enable(context);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntitySpawnEvent(dragon));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(crystals()).containsExactlyInAnyOrderElementsOf(first);
        assertThat(end.getEntitiesByClass(Zombie.class)).containsExactlyInAnyOrderElementsOf(firstDefenders);
        module.disable();
        module.disable();
        scheduler.advance(Duration.ofSeconds(120));
        assertThat(crystals()).isEmpty();
        assertThat(end.getEntitiesByClass(Zombie.class)).isEmpty();
        assertThat(dragon.getHealth()).isEqualTo(200);
    }

    @Test
    void reloadRejectsMalformedEnablementWithoutReplacingRunningOrNextAttemptTuning() throws Exception {
        module.enable(context);
        scheduler.advance(Duration.ofSeconds(1));
        int anchorCount = crystals().size();
        assertThat(anchorCount).isPositive();
        var next = config.copy();
        next.node("max-health").set(800);
        next.node("defender-count").set(6);
        module.onConfigReload(next);
        var invalid = next.copy();
        invalid.node("max-health").set(700);
        invalid.node("enabled").set("not-a-boolean");
        assertThatThrownBy(() -> module.onConfigReload(invalid)).isInstanceOf(RuntimeException.class);
        assertThat(dragon.getHealth()).isEqualTo(1000);
        var missing = end.getEntitiesByClass(Zombie.class).iterator().next();
        missing.remove();
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(end.getEntitiesByClass(Zombie.class)).hasSize(anchorCount * 4);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(60));
        assertThat(dragon.getHealth()).isEqualTo(200);
        player.teleport(new Location(end, 10, 64, 0));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(dragon.getHealth()).isEqualTo(800);
        assertThat(end.getEntitiesByClass(Zombie.class)).hasSize(anchorCount * 6);
        next.node("enabled").set(false);
        module.onConfigReload(next);
        assertThat(crystals()).isEmpty();
        assertThat(end.getEntitiesByClass(Zombie.class)).isEmpty();
        assertThat(dragon.getHealth()).isEqualTo(200);
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isFalse();
        next.node("enabled").set(true);
        module.onConfigReload(next);
        scheduler.advance(Duration.ofSeconds(120));
        assertThat(crystals()).isEmpty(); // Enable flags still require restart.
    }

    private void mark(Entity entity, String kind) {
        entity.getPersistentDataContainer().set(new NamespacedKey("kreiscraft", "crown_artifact"),
                PersistentDataType.STRING, kind);
    }

    @Test
    void unloadingCancelsPendingOwnedWorldEffectsAndObsoleteCallbacksCannotTouchTheNextAttempt() throws Exception {
        // Scheduling world effects through the public encounter boundary needs no unfinished attack.
        var clock = spy(scheduler);
        List<Runnable> queued = new java.util.ArrayList<>();
        // Paper can have queued a main-thread callback before its Task is cancelled.
        doAnswer(invocation -> {
            queued.add(invocation.getArgument(1));
            return invocation.callRealMethod();
        }).when(clock).runLater(any(Duration.class), any(Runnable.class));
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                clock, context.messages());
        context.events().register(encounter);
        encounter.start();
        clock.advance(Duration.ofSeconds(1));
        var indicator = end.spawn(player.getLocation(), EnderCrystal.class);
        encounter.own(indicator, "indicator");
        var delayed = encounter.runLater(Duration.ofSeconds(5), () -> player.setHealth(1));
        var repeating = encounter.runRepeating(Duration.ofSeconds(5), Duration.ofSeconds(1),
                () -> dragon.setHealth(1));
        var obsolete = List.copyOf(queued);
        server.getPluginManager().callEvent(new org.bukkit.event.world.WorldUnloadEvent(end));
        assertThat(indicator.isValid()).isFalse();
        assertThat(delayed.isCancelled()).isTrue();
        assertThat(repeating.isCancelled()).isTrue();
        server.getPluginManager().callEvent(new org.bukkit.event.world.WorldLoadEvent(end));
        clock.advance(Duration.ofSeconds(1));
        player.teleport(new Location(end, 100, 50, 0));
        clock.advance(Duration.ofSeconds(9));
        obsolete.forEach(Runnable::run);
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(dragon.getHealth()).isEqualTo(1000);
        assertThat(crystals()).isNotEmpty();
        encounter.close();
        encounter.close();
        clock.advance(Duration.ofSeconds(10));
        assertThat(crystals()).isEmpty();
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isFalse();
    }

    @Test
    void attemptTerminationRemovesOwnedIndicatorsAndRestoresTemporaryEffectsExactlyOnce() throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ofSeconds(1));
        player.setGlowing(true);
        encounter.onCleanup(() -> player.setGlowing(false));
        var indicator = end.spawn(player.getLocation(), EnderCrystal.class);
        encounter.own(indicator, "indicator");
        encounter.close();
        assertThat(indicator.isValid()).isFalse();
        assertThat(player.isGlowing()).isFalse();
        player.setGlowing(true);
        encounter.close();
        assertThat(player.isGlowing()).isTrue();
    }

    @Test
    void disabledChunkAndWorldDiscoveryNormalizeDamagedRecoveryMetadataWithoutDeletingUnrelatedEntities()
            throws Exception {
        config.node("enabled").set(false);
        module.enable(context);
        var stale = end.spawn(player.getLocation(), Zombie.class);
        mark(stale, "defender");
        var ordinary = end.spawn(player.getLocation(), EnderCrystal.class);
        var unrelated = end.spawn(player.getLocation(), Zombie.class);
        mark(player, "not-an-encounter-entity");
        dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);
        dragon.setHealth(40);
        dragon.getPersistentDataContainer().set(new NamespacedKey("kreiscraft", "crown_original_health"),
                PersistentDataType.DOUBLE, Double.NaN);
        var loaded = new org.bukkit.event.world.EntitiesLoadEvent(end.getChunkAt(0, 0),
                List.of(stale, ordinary, unrelated, player, dragon));
        server.getPluginManager().callEvent(loaded);
        server.getPluginManager().callEvent(loaded);
        server.getPluginManager().callEvent(new org.bukkit.event.world.WorldLoadEvent(end));
        scheduler.advance(Duration.ofSeconds(120));
        assertThat(stale.isValid()).isFalse();
        assertThat(ordinary.isValid()).isTrue();
        assertThat(unrelated.isValid()).isTrue();
        assertThat(player.isValid()).isTrue();
        assertThat(dragon.getHealth()).isEqualTo(200);
        assertThat(dragon.getPersistentDataContainer().has(new NamespacedKey("kreiscraft", "crown_original_health")))
                .isFalse();
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isFalse();
        assertThat(crystals()).containsExactly(ordinary);
    }

    @Test
    void ownedNativeLandingPhaseIsRestoredOnAbortAndRestartNeverResumesTheLanding() throws Exception {
        dragon.setPhase(EnderDragon.Phase.CIRCLING);
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ofSeconds(1));
        encounter.ownDragonPhase();
        dragon.setPhase(EnderDragon.Phase.LAND_ON_PORTAL);
        encounter.close();
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        dragon.getPersistentDataContainer().set(new NamespacedKey("kreiscraft", "crown_original_phase"),
                PersistentDataType.STRING, "CIRCLING");
        dragon.setPhase(EnderDragon.Phase.HOVER);
        config.node("enabled").set(false);
        module.enable(context);
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isFalse();
    }

    @Test
    void invalidStartupReportsTheOffendingSettingAndDoesNotPartiallyModifyTheBattle() throws Exception {
        config.node("max-health").set(800);
        config.node("defender-count").set(100000);
        assertThatThrownBy(() -> module.enable(context))
                .isInstanceOf(de.kreiscraft.core.module.ModuleException.class)
                .hasMessageContaining("defender-count");
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(dragon.getHealth()).isEqualTo(200);
        assertThat(crystals()).isEmpty();
        config.node("defender-count").set(4);
        config.node("enabled").set("true");
        assertThatThrownBy(() -> module.enable(context)).hasMessageContaining("enabled");
        config.node("enabled").set(true);
        module.enable(context);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(dragon.getHealth()).isEqualTo(800);
        assertThat(crystals()).isNotEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void resetAndVictoryCancelPendingEffectsAndLeaveRewardsToTheNormalBattle(boolean reset) throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ofSeconds(1));
        encounter.runLater(Duration.ofSeconds(65), () -> player.setHealth(1));
        var rewards = new org.bukkit.event.entity.EntityDeathEvent(dragon,
                mock(org.bukkit.damage.DamageSource.class), List.of(), 12000);
        if (reset) {
            player.teleport(new Location(end, 100, 50, 0));
        } else {
            for (int stage = 0; stage < 2; stage++) {
                end.getEntitiesByClass(Zombie.class).forEach(defender -> {
                    defender.setHealth(0);
                    server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDeathEvent(defender,
                            mock(org.bukkit.damage.DamageSource.class), List.of()));
                });
                for (EnderCrystal crystal : crystals()) {
                    server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDamageByEntityEvent(player,
                            crystal, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                            mock(org.bukkit.damage.DamageSource.class), 10));
                }
                if (stage == 0) {
                    dragon.setHealth(200);
                    server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDamageByEntityEvent(player,
                            dragon, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                            mock(org.bukkit.damage.DamageSource.class), 1000));
                }
            }
            server.getPluginManager().callEvent(rewards);
        }
        scheduler.advance(Duration.ofSeconds(70));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(crystals()).isEmpty();
        assertThat(end.getEntitiesByClass(Zombie.class)).isEmpty();
        assertThat(end.getEntitiesByClass(EnderDragon.class)).containsExactly(dragon);
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isFalse();
        if (!reset) {
            assertThat(rewards.getDroppedExp()).isEqualTo(12000);
            assertThat(rewards.isCancelled()).isFalse();
        }
        if (reset) {
            assertThat(dragon.getHealth()).isEqualTo(200);
            player.teleport(new Location(end, 10, 64, 0));
            scheduler.advance(Duration.ofSeconds(1));
            assertThat(crystals()).isNotEmpty();
        }
        encounter.close();
    }

    private List<EnderCrystal> crystals() {
        return end.getEntitiesByClass(EnderCrystal.class).stream().filter(Entity::isValid).toList();
    }
}
