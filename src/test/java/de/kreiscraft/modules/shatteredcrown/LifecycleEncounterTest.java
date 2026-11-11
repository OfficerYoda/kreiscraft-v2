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
            public void forgetDefenderTarget(Mob defender, org.bukkit.entity.Player player) {
                if (player.equals(defender.getTarget())) {
                    defender.setTarget(null); // MockBukkit omits Warden anger; native mode clearing is in FinalWaveProbe.
                }
            }

            @Override
            public de.kreiscraft.platform.DragonFlightControl controlFlight(EnderDragon source, Runnable tick) {
                return new SimulatedDragonFlight(source, scheduler, tick);
            }

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
            public void disableEquipmentPickup(Mob defender) {
                // Unsupported MockBukkit setter; OpeningRosterProbe asserts the real Paper equipment policy.
            }

            @Override
            public void faceWarningToViewer(org.bukkit.entity.TextDisplay warning) {
                // DisplayMock has no billboard setter; warning text, position and lifetime stay real.
            }

            @Override
            public void bombardmentDamage(org.bukkit.entity.Player recipient, EnderDragon source, double amount) {
                new org.mockbukkit.mockbukkit.simulate.entity.LivingEntitySimulation((PlayerMock) recipient)
                        .simulateDamage(amount,
                                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.DRAGON_BREATH)
                                        .withDirectEntity(source).withCausingEntity(source).build());
            }

            @Override
            public void prepareRangedDefender(Mob defender) {
                // Native navigation/beam are exercised by SecondWaveProbe, not MockBukkit.
            }

            @Override
            public void targetDefender(Mob defender, org.bukkit.entity.Player target) {
                // MockBukkit has no Brute/Breeze Brain; SecondWaveProbe checks the native transition.
                defender.setTarget(target);
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

    private List<org.bukkit.entity.Mob> defenders() {
        return end.getEntitiesByClass(org.bukkit.entity.Mob.class).stream().filter(Entity::isValid)
                .filter(mob -> mob != dragon && !(mob instanceof org.bukkit.entity.Enderman)).toList();
    }

    @AfterEach
    void tearDown() {
        module.disable();
        MockBukkit.unmock();
    }

    @Test
    void finalWaveRequiresEnhancedWitherModuleInsteadOfSilentlyFallingBackToVanilla() {
        assertThat(module.dependsOn()).contains("wither");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void registryRejectsCrownWhenWitherIsMissingOrDisabled(boolean registered) throws Exception {
        var root = BasicConfigurationNode.root();
        root.node("modules", module.id()).from(config);
        var registry = new de.kreiscraft.core.module.ModuleRegistry(ignored -> context, root, plugin.getLogger());
        registry.register(module);
        if (registered) {
            registry.register(new de.kreiscraft.modules.wither.WitherModule());
        }
        assertThatThrownBy(registry::enableAll)
                .isInstanceOf(de.kreiscraft.core.module.ModuleException.class)
                .hasMessageContaining("shattered-crown").hasMessageContaining("wither")
                .hasMessageContaining("not enabled or does not exist");
        assertThat(context.services().find(EncounterController.class)).isEmpty();
        assertThat(end.getEntitiesByClass(EnderCrystal.class)).isEmpty();
    }

    @Test
    void bothModulesUseRegistryStartupReloadAndReverseShutdownWithoutVanillaFallback() throws Exception {
        var root = BasicConfigurationNode.root();
        root.node("modules", module.id()).from(config);
        root.node("modules", "wither", "enabled").set(true);
        var registry = new de.kreiscraft.core.module.ModuleRegistry(candidate -> new ModuleContext(plugin,
                root.node("modules", candidate.id()), context.messages(), context.services(), scheduler,
                context.commands(), context.events()), root, plugin.getLogger());
        registry.register(module);
        registry.register(new de.kreiscraft.modules.wither.WitherModule());
        assertThat(registry.enableAll().enabled()).isEqualTo(2);
        assertThat(context.services().find(EncounterService.class)).isPresent();
        assertThat(context.services().find(EncounterController.class)).isEmpty();
        var encounter = java.util.Arrays.stream(org.bukkit.event.entity.EntityDamageEvent.getHandlerList()
                .getRegisteredListeners()).map(org.bukkit.plugin.RegisteredListener::getListener)
                .filter(EncounterController.class::isInstance).map(EncounterController.class::cast)
                .findFirst().orElseThrow();
        scheduler.advance(Duration.ZERO);
        encounter.debugWave(3, player);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.Wither.class))
                .allSatisfy(wither -> assertThat(wither.getHealth()).isEqualTo(600));

        root.node("modules", "wither", "max-health").set(800);
        root.node("modules", "wither", "enabled").set(false);
        registry.reloadConfigs(root);
        assertThat(registry.isEnabled("wither")).as("Wither enablement changes require restart").isTrue();
        encounter.debugWave(3, player);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.Wither.class))
                .allSatisfy(wither -> assertThat(wither.getHealth()).isEqualTo(800));

        root.node("modules", module.id(), "enabled").set(false);
        registry.reloadConfigs(root);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.Wither.class)).isEmpty();
        assertThat(end.getEntitiesByClass(org.bukkit.entity.Warden.class)).isEmpty();
        root.node("modules", module.id(), "enabled").set(true);
        registry.reloadConfigs(root);
        scheduler.advance(Duration.ofSeconds(12));
        assertThat(end.getEntitiesByClass(EnderCrystal.class)).isEmpty();
        registry.disableAll();
        assertThat(registry.isEnabled("wither")).isFalse();
        scheduler.advance(Duration.ofSeconds(12));
        assertThat(end.getEntitiesByClass(EnderCrystal.class)).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"1,1,4,8,2", "4,1,4,8,2", "5,2,8,16,4", "8,2,8,16,4",
            "12,3,12,24,6", "20,5,20,40,10", "68,17,68,136,34"})
    void finalWaveRepeatsOneWardenAndEnhancedWitherPairPerFrozenUnitAtEveryAnchor(
            int population, int units, int speciesCount, int total, long perAnchor) throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        for (int index = 0; index < population - 1; index++) {
            var observer = server.addPlayer();
            observer.teleport(new Location(end, 250, 64, 0));
            observer.setGameMode(index % 2 == 0 ? org.bukkit.GameMode.CREATIVE : org.bukkit.GameMode.SPECTATOR);
        }
        encounter.debugWave(3, player);
        assertThat(encounter.wavePopulation()).isEqualTo(population);
        assertThat(encounter.rosterUnits()).isEqualTo(units);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.Warden.class)).hasSize(speciesCount);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.Wither.class)).hasSize(speciesCount);
        assertThat(end.getEntitiesByClass(Mob.class).stream().filter(encounter::ownsCombatant)).hasSize(total);
        assertThat(end.getEntitiesByClass(Mob.class).stream().filter(encounter::ownsCombatant)
                .collect(java.util.stream.Collectors.groupingBy(
                        encounter::anchorId, java.util.stream.Collectors.counting()))
                .values()).containsOnly(perAnchor);
        server.addPlayer().teleport(new Location(end, 250, 64, 0));
        assertThat(end.getEntitiesByClass(Mob.class).stream().filter(encounter::ownsCombatant)).hasSize(total);
        encounter.close();
    }

    @Test
    void enhancedWitherMinionsJoinOriginalAnchorBeforeSpawnAndStillGateAfterParentDeath() throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        var witherConfig = new de.kreiscraft.modules.wither.WitherConfig(600, 40, 8, 300, 4,
                20, true, true, 4, true, true, 20, true, true, 50);
        var listener = new de.kreiscraft.modules.wither.WitherListener(() -> witherConfig,
                context.messages(), scheduler, () -> encounter);
        context.events().register(listener);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        encounter.debugWave(3, player);
        var parent = end.getEntitiesByClass(org.bukkit.entity.Wither.class).iterator().next();
        var anchor = encounter.anchorId(parent);
        assertThat(parent.getHealth()).isEqualTo(600);
        var spawns = new java.util.ArrayList<Entity>();
        context.events().register(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void minion(org.bukkit.event.entity.CreatureSpawnEvent event) {
                if (event.getEntity() instanceof org.bukkit.entity.WitherSkeleton) {
                    assertThat(encounter.anchorId(event.getEntity())).isEqualTo(anchor);
                    spawns.add(event.getEntity());
                }
            }
        });
        // Only the unsupported native explosion operation is adapted; native blasts get the Paper probe.
        var world = spy(end);
        doAnswer(call -> true).when(world).createExplosion(any(org.bukkit.Location.class),
                org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), any(Entity.class));
        parent.teleport(new Location(world, 150, 64, 10)); // Roam far from the original logical Anchor.
        parent.setHealth(300);
        var cancelled = new org.bukkit.event.entity.EntityDamageEvent(parent,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.mockito.Mockito.mock(org.bukkit.damage.DamageSource.class), 1000);
        cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        assertThat(spawns).isEmpty();
        var damage = new org.bukkit.event.entity.EntityDamageEvent(parent,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.mockito.Mockito.mock(org.bukkit.damage.DamageSource.class), 1000);
        server.getPluginManager().callEvent(damage);
        assertThat(spawns).hasSize(4);
        for (Mob boss : end.getEntitiesByClass(Mob.class).stream().filter(encounter::ownsCombatant)
                .filter(mob -> !(mob instanceof org.bukkit.entity.WitherSkeleton)).toList()) {
            boss.setHealth(0);
            server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDeathEvent(boss,
                    org.mockito.Mockito.mock(org.bukkit.damage.DamageSource.class), new java.util.ArrayList<>()));
        }
        assertThat(end.getEntitiesByClass(EnderCrystal.class).stream().filter(crystal -> !crystal.isGlowing()))
                .hasSize(1);
        assertThat(spawns).allMatch(encounter::ownsCombatant);
        encounter.close();
    }

    @Test
    void ownedWitherDeathDropsNoStarsAndSchedulesOneAttributedCountdownEvenWithDuplicateCallbacks()
            throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        var witherConfig = new de.kreiscraft.modules.wither.WitherConfig(600, 40, 8, 300, 4,
                20, true, true, 4, true, true, 20, true, true, 50);
        var listener = new de.kreiscraft.modules.wither.WitherListener(() -> witherConfig,
                context.messages(), scheduler, () -> encounter);
        context.events().register(listener);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        encounter.debugWave(3, player);
        var parent = end.getEntitiesByClass(org.bukkit.entity.Wither.class).iterator().next();
        var world = spy(end);
        doAnswer(call -> true).when(world).createExplosion(any(org.bukkit.Location.class),
                org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), any(Entity.class));
        parent.teleport(new Location(world, parent.getX(), parent.getY(), parent.getZ()));
        parent.setHealth(0);
        for (int callback = 0; callback < 2; callback++) {
            var drops = new java.util.ArrayList<>(List.of(new org.bukkit.inventory.ItemStack(Material.NETHER_STAR)));
            server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDeathEvent(parent,
                    org.mockito.Mockito.mock(org.bukkit.damage.DamageSource.class), drops));
            assertThat(drops).isEmpty();
        }
        scheduler.advance(Duration.ofSeconds(11));
        org.mockito.Mockito.verify(world, org.mockito.Mockito.times(1)).createExplosion(any(org.bukkit.Location.class),
                org.mockito.ArgumentMatchers.eq(20f), org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.eq(parent));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.Item.class)).isEmpty();
        encounter.onCleanup(() -> assertThat(encounter.ownsCombatant(parent)).isFalse());
        encounter.close();
        doAnswer(call -> true).when(world).createExplosion(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean());
        var staleDrops = new java.util.ArrayList<>(List.of(new org.bukkit.inventory.ItemStack(Material.NETHER_STAR)));
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDeathEvent(parent,
                org.mockito.Mockito.mock(org.bukkit.damage.DamageSource.class), staleDrops));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.Item.class)).isEmpty();
        scheduler.advance(Duration.ofSeconds(11));
        org.mockito.Mockito.verify(world, org.mockito.Mockito.never()).createExplosion(
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());
        listener.shutdown();
    }

    @Test
    void connectedDeadEndPlayersStillScaleAutomaticPreyAndLaterWaveSnapshots() throws Exception {
        // Paper removes connected dead players from the world's entity roster, unlike MockBukkit's default view.
        end = new WorldMock(new org.bukkit.WorldCreator("native-roster-end").environment(World.Environment.THE_END)) {
            @Override
            public List<org.bukkit.entity.Player> getPlayers() {
                return super.getPlayers().stream().filter(participant -> !participant.isDead()).toList();
            }
        };
        server.addWorld(end);
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                end.getBlockAt(x, 63, z).setType(Material.END_STONE);
            }
        }
        dragon = end.spawn(new Location(end, 0, 80, 0), EnderDragon.class);
        dragon.setPodium(new Location(end, 0, 64, 0));
        player.teleport(new Location(end, 10, 64, 0));
        var teammate = server.addPlayer();
        teammate.setGameMode(org.bukkit.GameMode.ADVENTURE);
        teammate.teleport(new Location(end, -10, 64, 0));
        var deadPlayers = List.of(server.addPlayer(), server.addPlayer());
        deadPlayers.forEach(connected -> connected.teleport(new Location(end, 15, 64, 0)));
        var elsewhere = server.addPlayer();
        elsewhere.teleport(new Location(server.addSimpleWorld("other-roster-world"), 0, 64, 0));
        config.node("world-key").set(end.getKey().toString());
        config.node("opening-grace-seconds").set(0);
        config.node("prey-cadence-seconds").set(5);
        config.node("lane-cadence-seconds").set(120);
        config.node("sweep-cadence-seconds").set(120);
        deadPlayers.forEach(connected -> connected.setHealth(0));
        var encounter = new EncounterController(server, battles, ShatteredCrownConfig.load(config),
                scheduler, context.messages(), new java.util.Random(4096)); // First production round starts Prey.
        context.events().register(encounter);
        try {
            encounter.start();
            scheduler.advance(Duration.ZERO);
            assertThat(encounter.firstEntryPopulation()).isEqualTo(5); // Server-wide snapshot stays unchanged.
            assertThat(deadPlayers).allSatisfy(connected -> {
                assertThat(connected.isOnline()).isTrue();
                assertThat(connected.isDead()).isTrue();
                assertThat(connected.getWorld()).isSameAs(end);
            });
            assertThat(server.getOnlinePlayers()).hasSize(5);
            assertThat(end.getPlayers()).containsExactlyInAnyOrder(player, teammate);
            assertThat(encounter.combatPopulation()).isEqualTo(2);
            clearWave();
            scheduler.advance(Duration.ZERO);
            assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).hasSize(2); // Four connected → two marks.
            assertThat(encounter.scalingPopulation()).isEqualTo(4);
            acceptedDragonHit(100000);
            assertThat(encounter.waveNumber()).isEqualTo(2);
            assertThat(encounter.wavePopulation()).isEqualTo(4);
            clearWave();
            acceptedDragonHit(100000);
            assertThat(encounter.waveNumber()).isEqualTo(3);
            assertThat(encounter.wavePopulation()).isEqualTo(4);
            assertThat(encounter.firstEntryPopulation()).isEqualTo(5);
        } finally {
            encounter.close();
        }
    }

    @Test
    void lateArrivalsCannotResizeOpeningRosterAndLaterWavesSnapshotEveryoneInEnd() throws Exception {
        var overworld = server.addSimpleWorld("population-overworld");
        player.teleport(new Location(overworld, 0, 64, 0));
        var others = new java.util.ArrayList<PlayerMock>();
        for (int index = 0; index < 11; index++) {
            var observer = server.addPlayer();
            observer.teleport(new Location(overworld, 0, 64, 0));
            observer.setGameMode(org.bukkit.GameMode.CREATIVE);
            others.add(observer);
        }
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        player.teleport(new Location(end, 100, 50, 0));
        server.addPlayer().teleport(new Location(overworld, 0, 64, 0)); // After first entry, before arena entry.
        player.teleport(new Location(end, 10, 64, 0));
        scheduler.advance(Duration.ZERO);
        assertThat(encounter.wavePopulation()).isEqualTo(12);
        assertThat(encounter.rosterUnits()).isEqualTo(3);
        assertThat(defenders()).hasSize(132);
        for (var observer : others.subList(0, 4)) {
            observer.teleport(new Location(end, 250, 64, 0));
        }
        assertThat(encounter.wavePopulation()).isEqualTo(12);
        assertThat(encounter.scalingPopulation()).isEqualTo(5);
        clearWave();
        acceptedDragonHit(100000);
        assertThat(encounter.waveNumber()).isEqualTo(2);
        assertThat(encounter.wavePopulation()).isEqualTo(5);
        assertThat(encounter.rosterUnits()).isEqualTo(2);
        assertThat(defenders()).hasSize(48);
        others.getFirst().teleport(new Location(overworld, 0, 64, 0));
        assertThat(encounter.wavePopulation()).isEqualTo(5);
        clearWave();
        acceptedDragonHit(100000);
        assertThat(encounter.waveNumber()).isEqualTo(3);
        assertThat(encounter.wavePopulation()).isEqualTo(4);
        assertThat(encounter.rosterUnits()).isEqualTo(1);
        assertThat(end.getEntitiesByClass(Mob.class).stream().filter(encounter::ownsCombatant)).hasSize(8);
        encounter.close();
    }

    private void clearWave() {
        end.getEntitiesByClass(Mob.class).stream()
                .filter(mob -> mob.getPersistentDataContainer().has(new NamespacedKey("kreiscraft", "crown_artifact")))
                .toList().forEach(defender -> {
                    defender.setHealth(0);
                    server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDeathEvent(defender,
                            mock(org.bukkit.damage.DamageSource.class), List.of()));
                });
        for (var crystal : crystals()) {
            server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDamageByEntityEvent(player, crystal,
                    org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    mock(org.bukkit.damage.DamageSource.class), 10));
        }
    }

    @Test
    void openingRoundsAlternateBothFamiliesAndGapBeginsAtMainCompletion() throws Exception {
        config.node("pool-damage").set(0);
        config.node("bombardment-damage").set(0);
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages(), new java.util.Random(7));
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        scheduler.advance(Duration.ofMillis(29950));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofMillis(50));
        assertThat(status(encounter)).contains("main=executing", "grace-seconds=0.0");
        var starts = new java.util.ArrayList<String>();
        String previous = "none";
        int completedAt = -1;
        for (int tick = 0; tick < 1800; tick++) {
            String state = status(encounter);
            String attack = state.split(", attack=")[1].split(",")[0];
            if (!attack.equals("none") && !attack.equals(previous)) {
                starts.add(attack);
                if (completedAt >= 0) {
                    assertThat(tick - completedAt).isBetween(100, 200);
                }
            }
            if (attack.equals("none") && !previous.equals("none")) {
                completedAt = tick;
            }
            previous = attack;
            scheduler.advance(Duration.ofMillis(50));
        }
        assertThat(starts.size()).isGreaterThanOrEqualTo(6);
        for (int index = 0; index + 1 < starts.size(); index += 2) {
            assertThat(starts.subList(index, index + 2)).containsExactlyInAnyOrder("PREY", "BOMBARDMENT");
        }
        for (int index = 1; index < starts.size(); index++) {
            assertThat(starts.get(index)).isNotEqualTo(starts.get(index - 1));
        }
        encounter.close();
    }

    private EncounterController rotationEncounter(java.util.Random random) throws Exception {
        var encounter = new EncounterController(server, battles, ShatteredCrownConfig.load(config),
                scheduler, context.messages(), random);
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        return encounter;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {5, 10})
    void configuredGapEndpointsWaitAfterImpactNotAfterTheWarningStarts(int seconds) throws Exception {
        config.node("opening-grace-seconds").set(0);
        config.node("attack-gap-min-seconds").set(seconds);
        config.node("attack-gap-max-seconds").set(seconds);
        config.node("bombardment-damage").set(0);
        config.node("pool-damage").set(0);
        var encounter = rotationEncounter(new java.util.Random(7));
        assertThat(status(encounter)).contains("attack=BOMBARDMENT");
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(status(encounter)).contains("main=idle", "next-warning-seconds=" + seconds + ".0");
        scheduler.advance(Duration.ofMillis(seconds * 1000 - 50));
        assertThat(status(encounter)).contains("main=idle");
        scheduler.advance(Duration.ofMillis(50));
        assertThat(status(encounter)).contains("attack=PREY");
        encounter.close();
    }

    @Test
    void newUnlockJoinsTheUnfinishedRoundAndEarlierFamiliesRunDuringLaterShields() throws Exception {
        config.node("opening-grace-seconds").set(0);
        config.node("attack-gap-min-seconds").set(5);
        config.node("attack-gap-max-seconds").set(5);
        config.node("bombardment-damage").set(0);
        config.node("pool-damage").set(0);
        config.node("lane-damage").set(0);
        var encounter = rotationEncounter(new java.util.Random(7));
        scheduler.advance(Duration.ofSeconds(3));
        clearWave();
        assertThat(encounter.availableAttacks()).containsExactlyInAnyOrder(EncounterController.AttackFamily.PREY,
                EncounterController.AttackFamily.BOMBARDMENT, EncounterController.AttackFamily.DIVE);
        assertThat(status(encounter)).contains("next-warning-seconds=5.0");
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(status(encounter)).contains("attack=PREY");
        scheduler.advance(Duration.ofSeconds(4));
        acceptedDragonHit(100000);
        assertThat(encounter.waveNumber()).isEqualTo(2);
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(status(encounter)).contains("attack=DIVE", "shield=true");
        encounter.debugPause();
        clearWave();
        assertThat(encounter.unlockedAttacks()).contains(EncounterController.AttackFamily.RAIN);
        assertThat(encounter.availableAttacks()).contains(EncounterController.AttackFamily.RAIN);
        acceptedDragonHit(100000);
        assertThat(encounter.availableAttacks()).doesNotContain(EncounterController.AttackFamily.SMASH);
        clearWave();
        assertThat(encounter.availableAttacks()).contains(EncounterController.AttackFamily.SMASH);
        encounter.close();
    }

    @Test
    void sixSecondLandingStartsAtArrivalAndDoesNotClearOrFreezeAnOldPool() throws Exception {
        config.node("opening-grace-seconds").set(0);
        config.node("attack-gap-min-seconds").set(5);
        config.node("attack-gap-max-seconds").set(5);
        config.node("landing-cadence-seconds").set(10);
        config.node("pool-lifetime-seconds").set(15);
        config.node("pool-damage").set(1);
        config.node("bombardment-damage").set(0);
        var encounter = rotationEncounter(new java.util.Random(0) {
            @Override
            public int nextInt(int bound) {
                return 0; // First shuffled family is Prey; gap randomness uses the other overload.
            }
        });
        clearWave();
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(status(encounter)).contains("main=idle", "hazards=1");
        scheduler.advance(Duration.ofMillis(8050));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.FLY_TO_PORTAL);
        assertThat(status(encounter)).contains("attack=LANDING", "hazards=1");
        scheduler.advance(Duration.ofMillis(950)); // Approach time cannot consume the melee window.
        dragon.teleport(new Location(end, 0, 64, 0));
        var arrival = new org.bukkit.event.entity.EnderDragonChangePhaseEvent(dragon, dragon.getPhase(),
                EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        server.getPluginManager().callEvent(arrival);
        dragon.setPhase(arrival.getNewPhase());
        assertThat(status(encounter)).contains("landing-window=true");
        double health = player.getHealth();
        scheduler.advance(Duration.ofMillis(5950));
        assertThat(status(encounter)).contains("attack=LANDING", "hazards=1");
        assertThat(player.getHealth()).isLessThan(health); // Old pool keeps delivering actual damage.
        scheduler.advance(Duration.ofMillis(50));
        assertThat(status(encounter)).contains("main=idle", "hazards=0");
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.LEAVE_PORTAL);
        encounter.close();
    }

    @Test
    void rejectedSetupsLostTargetsAndRepeatedResumeCannotStallOrDuplicateTheCoordinator() throws Exception {
        config.node("opening-grace-seconds").set(0);
        config.node("attack-gap-min-seconds").set(5);
        config.node("attack-gap-max-seconds").set(5);
        config.node("bombardment-damage").set(0);
        config.node("pool-damage").set(0);
        player.teleport(new Location(end, 70, 64, 0)); // No walkable warning footprint or pool deposition.
        var encounter = rotationEncounter(new java.util.Random(7));
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(status(encounter)).contains("attack=PREY");
        scheduler.advance(Duration.ofSeconds(4)); // Prey validates current terrain at deposition, not marking.
        assertThat(status(encounter)).contains("main=idle");
        player.teleport(new Location(end, 10, 64, 0));
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(status(encounter)).contains("attack=BOMBARDMENT");
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        scheduler.advance(Duration.ofMillis(250));
        assertThat(status(encounter)).contains("main=idle", "hazards=0");
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        scheduler.advance(Duration.ofMillis(4950));
        assertThat(status(encounter)).contains("main=idle");
        scheduler.advance(Duration.ofMillis(50));
        assertThat(status(encounter)).contains("attack=PREY");
        encounter.debugPause();
        scheduler.advance(Duration.ofSeconds(60));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        for (int repeat = 0; repeat < 4; repeat++) {
            encounter.debugResume();
            assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).hasSize(1);
        }
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).hasSizeLessThanOrEqualTo(1);
        encounter.close();
        scheduler.advance(Duration.ofSeconds(30));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
    }

    @Test
    void nativeWarningSetupExceptionStillAllowsTheOtherFamilyAtThePostFailureDeadline() throws Exception {
        config.node("opening-grace-seconds").set(0);
        config.node("attack-gap-min-seconds").set(5);
        config.node("attack-gap-max-seconds").set(5);
        config.node("pool-damage").set(0);
        battles = spy(battles);
        org.mockito.Mockito.doThrow(new IllegalStateException("native warning metadata failure"))
                .doCallRealMethod().when(battles).faceWarningToViewer(any());
        var encounter = rotationEncounter(new java.util.Random(7));
        assertThat(status(encounter)).contains("main=idle", "next-warning-seconds=5.0", "last-selection=BOMBARDMENT");
        scheduler.advance(Duration.ofMillis(4950));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofMillis(50));
        assertThat(status(encounter)).contains("attack=PREY");
        encounter.close();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {5, 10})
    void failedNativeLandingApproachYieldsToTheNextFamilyInsteadOfReservingAnotherLandingForever(int gap)
            throws Exception {
        config.node("opening-grace-seconds").set(0);
        config.node("attack-gap-min-seconds").set(gap);
        config.node("attack-gap-max-seconds").set(gap);
        config.node("landing-cadence-seconds").set(10);
        config.node("bombardment-damage").set(0);
        config.node("pool-damage").set(0);
        var encounter = rotationEncounter(new java.util.Random(7));
        clearWave();
        scheduler.advance(Duration.ofMillis(gap == 5 ? 12050 : 10000));
        assertThat(status(encounter)).contains("attack=LANDING", "landing-window=false");
        scheduler.advance(Duration.ofSeconds(20)); // Mock native dragon does not arrive; genuine approach timeout.
        assertThat(status(encounter)).contains("main=idle");
        scheduler.advance(Duration.ofSeconds(gap));
        assertThat(status(encounter)).contains(gap == 5 ? "attack=DIVE" : "attack=PREY");
        encounter.close();
    }

    @Test
    void overdueLandingCannotReplaceTheFirstRangedWarningAtGraceExpiry() throws Exception {
        config.node("landing-cadence-seconds").set(10);
        var encounter = rotationEncounter(new java.util.Random(7));
        clearWave();
        scheduler.advance(Duration.ofMillis(29950));
        assertThat(status(encounter)).contains("main=idle");
        scheduler.advance(Duration.ofMillis(50));
        assertThat(status(encounter)).contains("attack=BOMBARDMENT", "grace-seconds=0.0");
        encounter.close();
    }

    @Test
    void nativeFightOriginAtYZeroDoesNotMakeTheRealPodiumGroundAnImpossibleLanding() throws Exception {
        dragon.setPodium(new Location(end, 0, 0, 0)); // Paper reports fight origin, not the raised portal surface.
        var encounter = rotationEncounter(new java.util.Random(7));
        encounter.debugTrigger(EncounterController.DebugAttack.LANDING, player, player);
        assertThat(status(encounter)).contains("attack=LANDING");
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.FLY_TO_PORTAL);
        dragon.teleport(new Location(end, 0, 64, 0));
        var arrival = new org.bukkit.event.entity.EnderDragonChangePhaseEvent(dragon, dragon.getPhase(),
                EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        server.getPluginManager().callEvent(arrival);
        dragon.setPhase(arrival.getNewPhase());
        assertThat(status(encounter)).contains("landing-window=true");
        scheduler.advance(Duration.ofMillis(5950));
        assertThat(status(encounter)).contains("attack=LANDING");
        scheduler.advance(Duration.ofMillis(50));
        assertThat(status(encounter)).contains("main=idle");
        encounter.close();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"attack-gap-min-seconds,4", "attack-gap-max-seconds,11",
            "attack-gap-min-seconds,NaN", "attack-gap-max-seconds,Infinity", "landing-window-seconds,12"})
    void rotationTimingRejectsOutOfContractValues(String key, double value) throws Exception {
        config.node(key).set(value);
        assertThatThrownBy(() -> ShatteredCrownConfig.load(config)).isInstanceOf(Exception.class);
    }

    @Test
    void reversedGapBoundsCannotPublish() throws Exception {
        config.node("attack-gap-min-seconds").set(9);
        config.node("attack-gap-max-seconds").set(6);
        assertThatThrownBy(() -> ShatteredCrownConfig.load(config)).hasRootCauseMessage(
                "shattered-crown.attack-gap-max-seconds must be finite and between 9.0 and 10.0");
    }

    private void acceptedDragonHit(double amount) {
        var hit = new org.bukkit.event.entity.EntityDamageByEntityEvent(player, dragon,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                mock(org.bukkit.damage.DamageSource.class), amount);
        server.getPluginManager().callEvent(hit);
        assertThat(hit.isCancelled()).isFalse();
        dragon.setHealth(dragon.getHealth() - hit.getFinalDamage());
        scheduler.advance(Duration.ZERO);
    }

    @Test
    void deadParentAttributionSurvivesNormalWaveTransitionsButNotDebugReplacement() throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        var parent = defenders().iterator().next();
        var originalAnchor = encounter.anchorId(parent);
        var unrelated = end.spawn(player.getLocation(), org.bukkit.entity.Enderman.class);
        var source = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.EXPLOSION)
                .withDirectEntity(parent).withCausingEntity(parent).build();
        for (int wave = 2; wave <= 3; wave++) {
            clearWave();
            acceptedDragonHit(100000);
            assertThat(encounter.waveNumber()).isEqualTo(wave);
            assertThat(encounter.anchorId(parent)).isEqualTo(originalAnchor);
            var blast = new org.bukkit.event.entity.EntityDamageEvent(unrelated,
                    org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, source, 10);
            server.getPluginManager().callEvent(blast);
            assertThat(blast.isCancelled()).isTrue();
        }
        encounter.debugWave(1, server.getConsoleSender());
        assertThat(encounter.ownsCombatant(parent)).isFalse();
        encounter.close();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"4,1,43", "5,2,87", "8,2,87", "12,3,132", "20,5,219", "68,17,747"})
    void populationUsesFullUncappedRosterUnitsAtEveryAnchor(int population, int units, int total) throws Exception {
        for (int index = 1; index < population; index++) {
            server.addPlayer();
        }
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        encounter.start();
        scheduler.advance(Duration.ZERO);
        assertThat(encounter.wavePopulation()).isEqualTo(population);
        assertThat(encounter.rosterUnits()).isEqualTo(units);
        assertThat(defenders()).hasSize(total);
        for (var anchor : crystals()) {
            assertThat(defenders().stream()
                    .filter(defender -> defender.getLocation().distanceSquared(anchor.getLocation()) < 25))
                            .hasSizeBetween(10 * units, 12 * units);
            assertThat(defenders().stream().filter(mob -> mob.getVehicle() == null)
                    .filter(mob -> mob.getLocation().distanceSquared(anchor.getLocation()) < 25)).hasSize(4 * units);
        }
        encounter.close();
    }

    @Test
    void aDebugWaveWithNobodyInEndHasZeroRosterUnitsButStillRequiresFourCrystalDestructions() throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        player.teleport(new Location(server.addSimpleWorld("empty-end-observer"), 0, 64, 0));
        encounter.debugWave(3, server.getConsoleSender());
        assertThat(encounter.wavePopulation()).isZero();
        assertThat(encounter.rosterUnits()).isZero();
        assertThat(defenders()).isEmpty();
        assertThat(crystals()).hasSize(4).allMatch(Entity::isGlowing);
        assertThat(encounter.unlockedAttacks()).doesNotContain(EncounterController.AttackFamily.SMASH);
        encounter.close();
    }

    @Test
    void debugReplacementKeepsTheContinuousAbsenceDeadlineAndRemainingGrace() throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        scheduler.advance(Duration.ofSeconds(10));
        encounter.debugWave(2, player);
        assertThat(encounter.remainingGraceSeconds()).isEqualTo(20);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(59));
        encounter.debugWave(3, player);
        assertThat(crystals()).hasSize(4);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).isEmpty();
        assertThat(encounter.waveNumber()).isZero();
        encounter.close();
    }

    @Test
    void ownedWitherSideHeadsDropPlayersWhoBecomeProtectedOrIneligible() throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        var parent = defenders().iterator().next();
        var wither = (org.bukkit.entity.Wither) encounter.spawnCombatant(parent.getLocation(),
                org.bukkit.entity.EntityType.WITHER, parent, battles::preventDespawn);
        for (var head : org.bukkit.entity.Wither.Head.values()) {
            wither.setTarget(head, player);
        }
        player.teleport(new Location(end, 100, 50, 0));
        for (var head : org.bukkit.entity.Wither.Head.values()) {
            assertThat(wither.getTarget(head)).isNull();
        }
        encounter.close();
    }

    @Test
    void missingDescendantsRecoverTheirSpeciesAndTuningButRepeatedLossAbortsInsteadOfLockingAnAnchor()
            throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        var parent = defenders().iterator().next();
        var anchorId = encounter.anchorId(parent);
        var originals = end.getEntitiesByClass(Mob.class).stream().map(Entity::getUniqueId).toList();
        Mob missing = encounter.spawnCombatant(parent.getLocation(), org.bukkit.entity.EntityType.HUSK, parent, mob -> {
            battles.preventDespawn(mob);
            mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(45);
            mob.setHealth(45);
        });
        for (int loss = 0; loss < 3; loss++) {
            var old = missing;
            old.remove(); // Missing/unloaded is not an accepted death notification.
            scheduler.advance(Duration.ofSeconds(1));
            missing = end.getEntitiesByClass(Mob.class).stream().filter(Entity::isValid)
                    .filter(mob -> mob.getType() == org.bukkit.entity.EntityType.HUSK && mob != old)
                    .filter(mob -> !originals.contains(mob.getUniqueId()))
                    .findFirst().orElseThrow();
            assertThat(encounter.anchorId(missing)).isEqualTo(anchorId);
            assertThat(missing.getHealth()).isEqualTo(45);
            assertThat(crystals()).hasSize(4).noneMatch(Entity::isGlowing);
        }
        var unrelated = end.spawn(player.getLocation(), org.bukkit.entity.Enderman.class);
        missing.remove();
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).isEmpty();
        assertThat(end.getEntitiesByClass(Mob.class).stream().filter(Entity::isValid))
                .containsExactlyInAnyOrder(dragon, unrelated);
        scheduler.advance(Duration.ofSeconds(31));
        assertThat(crystals()).hasSize(4);
        assertThat(unrelated.isValid()).isTrue();
        encounter.close();
    }

    @Test
    void debugWaveCommandsReplaceOwnedWorkFreezePopulationAndKeepEarlierUnlocks() throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        var dispatcher =
                new com.mojang.brigadier.CommandDispatcher<io.papermc.paper.command.brigadier.CommandSourceStack>();
        dispatcher.getRoot().addChild(DragonDebugCommand.build(() -> encounter, context.messages(), server));
        var source = mock(io.papermc.paper.command.brigadier.CommandSourceStack.class);
        org.mockito.Mockito.when(source.getSender()).thenReturn(server.getConsoleSender());
        var unrelated = end.spawn(new Location(end, 50, 64, 0), Zombie.class);
        var pending = new java.util.concurrent.atomic.AtomicInteger();
        context.events().register(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void spawning(org.bukkit.event.entity.CreatureSpawnEvent event) {
                if (event.getEntity() instanceof Mob && event.getEntity() != dragon) {
                    assertThat(encounter.ownsCombatant(event.getEntity())).isTrue();
                    assertThat(encounter.anchorId(event.getEntity())).isNotNull();
                }
            }
        });
        for (int index = 0; index < 4; index++) {
            var observer = server.addPlayer();
            observer.teleport(new Location(end, 250, 64, 0));
            observer.setGameMode(org.bukkit.GameMode.SPECTATOR);
        }
        for (int wave : new int[] {3, 1, 2, 3}) {
            var old = crystals();
            var parent = defenders().stream().filter(encounter::ownsCombatant)
                    .findFirst().orElseThrow();
            var child = encounter.spawnCombatant(parent.getLocation(), org.bukkit.entity.EntityType.HUSK, parent,
                    battles::preventDespawn);
            var projectile = end.spawn(child.getLocation(), org.bukkit.entity.Arrow.class,
                    shot -> assertThat(encounter.inheritOwnership(shot, child)).isTrue());
            var oldMembers = end.getEntitiesByClass(Mob.class).stream().filter(encounter::ownsCombatant).toList();
            var externalEffect = end.spawn(new Location(end, 50, 64, 0), org.bukkit.entity.TextDisplay.class);
            encounter.own(externalEffect, "wave-effect");
            encounter.runLater(Duration.ofSeconds(1), pending::incrementAndGet);
            dispatcher.execute("dragondebug wave " + wave, source);
            assertThat(externalEffect.isValid()).isFalse();
            assertThat(projectile.isValid()).isFalse();
            assertThat(encounter.ownsCombatant(projectile)).isFalse();
            assertThat(oldMembers).noneMatch(Entity::isValid).noneMatch(encounter::ownsCombatant);
            assertThat(old).noneMatch(Entity::isValid);
            assertThat(crystals()).hasSize(4).noneMatch(Entity::isGlowing);
            assertThat(defenders()).hasSize(wave == 1 ? 88 : wave == 2 ? 49 : 17).contains(unrelated);
            assertThat(dragon.getHealth()).isEqualTo(wave == 1 ? 1000 : wave == 2 ? 650 : 300);
            assertThat(status(encounter)).contains("wave=" + wave, "wave-population=5", "roster-units=2",
                    "mode=manual", "shield=true", "objectives=0/4");
            assertThat(encounter.unlockedAttacks()).contains(EncounterController.AttackFamily.BOMBARDMENT,
                    EncounterController.AttackFamily.PREY);
            assertThat(encounter.unlockedAttacks().contains(EncounterController.AttackFamily.DIVE)).isEqualTo(wave > 1);
            assertThat(encounter.unlockedAttacks().contains(EncounterController.AttackFamily.RAIN)).isEqualTo(wave > 2);
            assertThat(encounter.unlockedAttacks()).doesNotContain(EncounterController.AttackFamily.SMASH);
            scheduler.advance(Duration.ofSeconds(35));
            assertThat(pending.get()).isZero();
            assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        }
        dispatcher.execute("dragondebug resume", source);
        assertThat(status(encounter)).contains("mode=automatic", "wave=3", "shield=true");
        assertThat(status(encounter)).contains("main=executing");
        encounter.close();
        assertThat(crystals()).isEmpty();
        assertThat(defenders()).containsExactly(unrelated);
        assertThat(encounter.unlockedAttacks()).isEmpty();
        assertThat(encounter.wavePopulation()).isZero();
    }

    @Test
    void ownershipExistsBeforeSpawnAndDescendantsKeepTheOriginalGateAfterParentDeath() throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void spawning(org.bukkit.event.entity.CreatureSpawnEvent event) {
                if (event.getEntity() instanceof Zombie) {
                    assertThat(encounter.ownsCombatant(event.getEntity())).isTrue();
                    var retaliate = new org.bukkit.event.entity.EntityTargetLivingEntityEvent(event.getEntity(), dragon,
                            org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY);
                    server.getPluginManager().callEvent(retaliate);
                    assertThat(retaliate.isCancelled()).isTrue();
                }
            }
        }, plugin);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        var parent = defenders().iterator().next();
        var anchorId = encounter.anchorId(parent);
        var anchor =
                crystals().stream().filter(crystal -> crystal.getUniqueId().equals(anchorId)).findFirst().orElseThrow();
        var child = encounter.spawnCombatant(parent.getLocation(), org.bukkit.entity.EntityType.HUSK, parent,
                battles::preventDespawn);
        assertThat(encounter.anchorId(child)).isEqualTo(anchorId);
        var arrow = end.spawn(child.getLocation(), org.bukkit.entity.Arrow.class, shot -> {
            assertThat(encounter.inheritOwnership(shot, child)).isTrue();
            shot.setShooter(child);
        });
        assertThat(encounter.anchorId(arrow)).isEqualTo(anchorId);
        var cloud = end.spawn(child.getLocation(), org.bukkit.entity.AreaEffectCloud.class,
                effect -> assertThat(encounter.inheritOwnership(effect, child)).isTrue());
        var arrival = server.addPlayer();
        arrival.teleport(new Location(end, 100, 50, 0));
        var apply = new org.bukkit.event.entity.AreaEffectCloudApplyEvent(cloud,
                new java.util.ArrayList<>(List.of(arrival, player, dragon, child)));
        server.getPluginManager().callEvent(apply);
        assertThat(apply.getAffectedEntities()).containsExactly(player);
        for (var mob : List.copyOf(defenders())) {
            if (mob != child && anchorId.equals(encounter.anchorId(mob))) {
                mob.setHealth(0);
                server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDeathEvent(mob,
                        mock(org.bukkit.damage.DamageSource.class), List.of()));
            }
        }
        assertThat(anchor.isGlowing()).isFalse();
        child.teleport(new Location(end, -150, end.getMaxHeight() - 1, 0));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(child.getLocation().getX()).isEqualTo(-150);
        assertThat(anchor.isGlowing()).isFalse();
        var delayed = encounter.spawnCombatant(parent.getLocation(), org.bukkit.entity.EntityType.HUSK, parent,
                battles::preventDespawn);
        assertThat(encounter.anchorId(delayed)).isEqualTo(anchorId);
        var unrelated = end.spawn(player.getLocation(), org.bukkit.entity.Enderman.class);
        assertThat(encounter.inheritOwnership(unrelated, unrelated)).isFalse();
        encounter.close();
        assertThat(child.isValid()).isFalse();
        assertThat(delayed.isValid()).isFalse();
        assertThat(arrow.isValid()).isFalse();
        assertThat(cloud.isValid()).isFalse();
        assertThat(unrelated.isValid()).isTrue();
    }

    @Test
    void combatCylinderCoversFullBuildHeightAtRadius192ButExcludesArrivalAndOtherWorlds() throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        for (double height : new double[] {end.getMinHeight(), 39, end.getMaxHeight() - 1}) {
            player.teleport(new Location(end, 192, height, 0));
            assertThat(encounter.isParticipant(player)).as("height=%s location=%s health=%s status=%s", height,
                    player.getLocation(), player.getHealth(), status(encounter)).isTrue();
        }
        player.teleport(new Location(end, 192.01, 64, 0));
        assertThat(encounter.isParticipant(player)).isFalse();
        player.teleport(new Location(end, 100, 50, 0));
        assertThat(encounter.isParticipant(player)).isFalse();
        var other = server.addSimpleWorld("other-end");
        other.setEnvironment(World.Environment.THE_END);
        player.teleport(new Location(other, 0, 64, 0));
        assertThat(encounter.isParticipant(player)).isFalse();
        encounter.close();
    }

    @Test
    void firstEntryFreezesEveryoneServerWideBeforeArenaEntryAndRecoveryUsesDiscoveryPopulation() throws Exception {
        var overworld = server.addSimpleWorld("overworld");
        player.teleport(new Location(overworld, 0, 64, 0));
        var creative = server.addPlayer();
        creative.teleport(new Location(overworld, 0, 64, 0));
        creative.setGameMode(org.bukkit.GameMode.CREATIVE);
        var spectator = server.addPlayer();
        spectator.teleport(new Location(overworld, 0, 64, 0));
        spectator.setGameMode(org.bukkit.GameMode.SPECTATOR);
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        player.teleport(new Location(end, 100, 50, 0));
        assertThat(encounter.firstEntryPopulation()).isEqualTo(3);
        assertThat(crystals()).isEmpty();
        var late = server.addPlayer();
        late.teleport(new Location(overworld, 0, 64, 0));
        creative.teleport(new Location(end, 250, 64, 0));
        spectator.teleport(new Location(end, 0, 64, 0));
        assertThat(encounter.scalingPopulation()).isEqualTo(3);
        player.teleport(new Location(end, 10, 64, 0));
        scheduler.advance(Duration.ZERO);
        assertThat(encounter.firstEntryPopulation()).isEqualTo(3);
        assertThat(encounter.combatPopulation()).isEqualTo(1);
        assertThat(status(encounter)).contains("combat-population=1", "scaling-population=3", "entry-snapshot=3");
        encounter.close();
        assertThat(encounter.firstEntryPopulation()).isZero();
        var recovered = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        recovered.start();
        scheduler.advance(Duration.ZERO);
        assertThat(recovered.firstEntryPopulation()).isEqualTo(4);
        late.teleport(new Location(end, 250, 64, 0));
        assertThat(recovered.scalingPopulation()).isEqualTo(4);
        assertThat(recovered.firstEntryPopulation()).isEqualTo(4);
        recovered.close();
    }

    @Test
    void openingSnapshotSurvivesWhenEveryConnectedEndPlayerDiesBeforeCombatEntry() throws Exception {
        end = new WorldMock(new org.bukkit.WorldCreator("dead-arrival-end").environment(World.Environment.THE_END)) {
            @Override
            public List<org.bukkit.entity.Player> getPlayers() {
                return super.getPlayers().stream().filter(connected -> !connected.isDead()).toList();
            }
        };
        server.addWorld(end);
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                end.getBlockAt(x, 63, z).setType(Material.END_STONE);
            }
        }
        dragon = end.spawn(new Location(end, 0, 80, 0), EnderDragon.class);
        dragon.setPodium(new Location(end, 0, 64, 0));
        var elsewhere = server.addSimpleWorld("before-arrival");
        player.teleport(new Location(elsewhere, 0, 64, 0));
        for (int count = 0; count < 3; count++) {
            server.addPlayer().teleport(new Location(elsewhere, 0, 64, 0));
        }
        config.node("world-key").set(end.getKey().toString());
        var encounter = new EncounterController(server, battles, ShatteredCrownConfig.load(config),
                scheduler, context.messages());
        context.events().register(encounter);
        try {
            encounter.start();
            player.teleport(new Location(end, 100, 50, 0));
            assertThat(encounter.firstEntryPopulation()).isEqualTo(4);
            player.setHealth(0);
            assertThat(player.isOnline()).isTrue();
            assertThat(end.getPlayers()).isEmpty();
            scheduler.advance(Duration.ofSeconds(2));
            assertThat(encounter.firstEntryPopulation()).isEqualTo(4);
            var late = server.addPlayer();
            late.teleport(new Location(end, 10, 64, 0));
            scheduler.advance(Duration.ofSeconds(1));
            assertThat(server.getOnlinePlayers()).hasSize(5);
            assertThat(encounter.waveNumber()).isEqualTo(1);
            assertThat(encounter.wavePopulation()).isEqualTo(4);
            assertThat(encounter.rosterUnits()).isEqualTo(1);
            assertThat(defenders()).hasSize(43); // Replacing four with five would spawn two complete units.
        } finally {
            encounter.close();
        }
    }

    @Test
    void openingGraceKeepsDefendersActiveAndNativeAttacksQuietUntilTheExactBoundary() throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        assertThat(status(encounter)).contains("grace-seconds=30.0");
        assertThat(encounter.remainingGraceSeconds()).isEqualTo(30);
        assertThat(crystals()).hasSize(4);
        assertThat(defenders()).hasSize(43);
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        var nativePhase = new org.bukkit.event.entity.EnderDragonChangePhaseEvent(dragon,
                EnderDragon.Phase.CIRCLING, EnderDragon.Phase.STRAFING);
        server.getPluginManager().callEvent(nativePhase);
        assertThat(nativePhase.isCancelled()).isTrue();
        var nativeHit = new org.bukkit.event.entity.EntityDamageByEntityEvent(dragon, player,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                mock(org.bukkit.damage.DamageSource.class), 10);
        server.getPluginManager().callEvent(nativeHit);
        assertThat(nativeHit.isCancelled()).isTrue();
        var part = mock(org.bukkit.entity.ComplexEntityPart.class);
        org.mockito.Mockito.when(part.getParent()).thenReturn(dragon);
        var multipartHit = new org.bukkit.event.entity.EntityDamageByEntityEvent(part, player,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                mock(org.bukkit.damage.DamageSource.class), 10);
        server.getPluginManager().callEvent(multipartHit);
        assertThat(multipartHit.isCancelled()).isTrue();
        var fireball = end.spawn(dragon.getLocation(), org.bukkit.entity.DragonFireball.class);
        fireball.setShooter(dragon);
        var launch = new org.bukkit.event.entity.ProjectileLaunchEvent(fireball);
        server.getPluginManager().callEvent(launch);
        assertThat(launch.isCancelled()).isTrue();
        var cloud = end.spawn(dragon.getLocation(), org.bukkit.entity.AreaEffectCloud.class);
        cloud.setSource(dragon);
        var applied = new org.bukkit.event.entity.AreaEffectCloudApplyEvent(cloud,
                new java.util.ArrayList<>(List.of(player)));
        server.getPluginManager().callEvent(applied);
        assertThat(applied.getAffectedEntities()).isEmpty();
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        encounter.debugPause();
        encounter.debugResume();
        var arrival = server.addPlayer();
        arrival.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofMillis(19999));
        assertThat(encounter.remainingGraceSeconds()).isEqualTo(0.05);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofMillis(1));
        assertThat(status(encounter)).contains("grace-seconds=0.0");
        assertThat(encounter.remainingGraceSeconds()).isZero();
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).hasSize(1);
        var resumed = new org.bukkit.event.entity.EnderDragonChangePhaseEvent(dragon,
                EnderDragon.Phase.CIRCLING, EnderDragon.Phase.STRAFING);
        server.getPluginManager().callEvent(resumed);
        assertThat(resumed.isCancelled()).isFalse();
        encounter.close();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(doubles = {-1, 121, Double.NaN, Double.POSITIVE_INFINITY})
    void openingGraceConfigurationRejectsInvalidCandidatesWithoutChangingAnAttempt(double value) throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        encounter.start();
        scheduler.advance(Duration.ZERO);
        var invalid = config.copy();
        invalid.node("opening-grace-seconds").set(value);
        assertThatThrownBy(() -> encounter.configure(ShatteredCrownConfig.load(invalid)))
                .hasRootCauseMessage("shattered-crown.opening-grace-seconds must be finite and between 0.0 and 120.0");
        assertThat(status(encounter)).contains("grace-seconds=30.0");
        encounter.close();
    }

    @Test
    void clearingOpeningAnchorsDuringGraceDoesNotStartAttacksOrRestartPreparation() throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ofSeconds(5));
        defenders().forEach(defender -> {
            defender.setHealth(0);
            server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDeathEvent(defender,
                    mock(org.bukkit.damage.DamageSource.class), List.of()));
        });
        crystals().forEach(crystal -> server.getPluginManager().callEvent(
                new org.bukkit.event.entity.EntityDamageByEntityEvent(player, crystal,
                        org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                        mock(org.bukkit.damage.DamageSource.class), 10)));
        assertThat(crystals()).isEmpty();
        assertThat(status(encounter)).contains("grace-seconds=25.0");
        scheduler.advance(Duration.ofMillis(24999));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofMillis(1));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).hasSize(1);
        encounter.close();
    }

    @Test
    void graceReloadAppliesNextAttemptAndManualTriggerDoesNotRestartTheGraceClock() throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ofSeconds(5));
        encounter.debugTrigger(EncounterController.DebugAttack.BOMBARDMENT, player, player);
        assertThat(player.nextMessage()).contains("BOMBARDMENT");
        assertThat(status(encounter)).contains("grace-seconds=25.0");
        encounter.debugCancel();
        var next = config.copy();
        next.node("opening-grace-seconds").set(10);
        next.node("arena-radius").set(128);
        encounter.configure(ShatteredCrownConfig.load(next));
        player.teleport(new Location(end, 190, 64, 0));
        assertThat(encounter.isParticipant(player)).isTrue();
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(60));
        assertThat(crystals()).isEmpty();
        assertThat(encounter.firstEntryPopulation()).isZero();
        player.teleport(new Location(end, 10, 64, 0));
        scheduler.advance(Duration.ZERO);
        assertThat(status(encounter)).contains("grace-seconds=10.0");
        player.teleport(new Location(end, 190, 64, 0));
        assertThat(encounter.isParticipant(player)).isFalse();
        encounter.close();
        scheduler.advance(Duration.ofSeconds(30));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
    }

    @Test
    void allEndPopulationHasNoLegacyTargetLimitAndModeChangesDoNotSustainAnAttempt() throws Exception {
        var encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class),
                scheduler, context.messages());
        for (int i = 0; i < 65; i++) {
            var observer = server.addPlayer();
            observer.setGameMode(i % 2 == 0 ? org.bukkit.GameMode.CREATIVE : org.bukkit.GameMode.SPECTATOR);
            observer.teleport(new Location(end, 250, 64, 0));
        }
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        assertThat(encounter.scalingPopulation()).isEqualTo(66);
        assertThat(encounter.firstEntryPopulation()).isEqualTo(66);
        assertThat(encounter.combatPopulation()).isEqualTo(1);
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        scheduler.advance(Duration.ofMillis(59999));
        assertThat(crystals()).hasSize(4);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(crystals()).isEmpty();
        assertThat(encounter.firstEntryPopulation()).isZero();
        assertThat(encounter.scalingPopulation()).isEqualTo(66);
        encounter.close();
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
        assertThat(defenders()).isEmpty();
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

            @Override
            public void disableEquipmentPickup(Mob defender) {
                // Unsupported MockBukkit setter; OpeningRosterProbe asserts the real Paper equipment policy.
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
        assertThat(defenders()).isEmpty();
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
        var firstDefenders = List.copyOf(defenders());
        module.enable(context);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntitySpawnEvent(dragon));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(crystals()).containsExactlyInAnyOrderElementsOf(first);
        assertThat(defenders()).containsExactlyInAnyOrderElementsOf(firstDefenders);
        module.disable();
        module.disable();
        scheduler.advance(Duration.ofSeconds(120));
        assertThat(crystals()).isEmpty();
        assertThat(defenders()).isEmpty();
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
        var missing = defenders().iterator().next();
        missing.remove();
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(defenders()).hasSize(43);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(60));
        assertThat(dragon.getHealth()).isEqualTo(200);
        player.teleport(new Location(end, 10, 64, 0));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(dragon.getHealth()).isEqualTo(800);
        assertThat(defenders()).hasSize(43);
        next.node("enabled").set(false);
        module.onConfigReload(next);
        assertThat(crystals()).isEmpty();
        assertThat(defenders()).isEmpty();
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
            for (int stage = 0; stage < 3; stage++) {
                defenders().forEach(defender -> {
                    defender.setHealth(0);
                    server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDeathEvent(defender,
                            mock(org.bukkit.damage.DamageSource.class), List.of()));
                });
                for (EnderCrystal crystal : crystals()) {
                    server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDamageByEntityEvent(player,
                            crystal, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                            mock(org.bukkit.damage.DamageSource.class), 10));
                }
                if (stage < 2) {
                    var crossing = new org.bukkit.event.entity.EntityDamageByEntityEvent(player,
                            dragon, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                            mock(org.bukkit.damage.DamageSource.class), 1000);
                    server.getPluginManager().callEvent(crossing);
                    assertThat(crossing.isCancelled()).isFalse();
                    dragon.setHealth(dragon.getHealth() - crossing.getFinalDamage());
                    scheduler.advance(Duration.ZERO);
                }
            }
            server.getPluginManager().callEvent(rewards);
        }
        scheduler.advance(Duration.ofSeconds(70));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(crystals()).isEmpty();
        assertThat(defenders()).isEmpty();
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

    private String status(EncounterController encounter) {
        while (player.nextMessage() != null) {
            // Read only this explicit debug response, not earlier command output.
        }
        encounter.debugStatus(player);
        return player.nextMessage();
    }
}
