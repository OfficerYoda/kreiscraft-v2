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
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.spongepowered.configurate.BasicConfigurationNode;

/** Approved encounter/public-event seam. Native movement/attacks are checked by SecondWaveProbe. */
class SecondWaveEncounterTest {
    @org.junit.jupiter.api.extension.RegisterExtension
    static final org.junit.jupiter.api.extension.TestExecutionExceptionHandler NO_SILENT_SKIPS = (context, error) -> {
        if (error instanceof org.opentest4j.TestAbortedException) {
            throw new AssertionError("Unsupported MockBukkit behavior belongs at the Paper boundary", error);
        }
        throw error;
    };
    private static final Map<EntityType, Long> UNIT = Map.of(EntityType.RAVAGER, 1L, EntityType.GUARDIAN, 1L,
            EntityType.PIGLIN_BRUTE, 1L, EntityType.VINDICATOR, 1L, EntityType.BLAZE, 1L, EntityType.BREEZE, 1L);
    @TempDir
    Path data;
    private ServerMock server;
    private WorldMock end;
    private EnderDragon dragon;
    private PlayerMock player;
    private MockScheduler scheduler;
    private EncounterController encounter;

    @BeforeEach
    void start() throws Exception {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
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
        var config = BasicConfigurationNode.root();
        config.node("world-key").set(end.getKey().toString());
        var battles = new DragonBattleAccess() {
            @Override
            public org.bukkit.boss.DragonBattle battle(World world) {
                return world == end ? dragon.getDragonBattle() : null;
            }

            @Override
            public de.kreiscraft.platform.DragonFlightControl controlFlight(EnderDragon source, Runnable tick) {
                return new SimulatedDragonFlight(source, scheduler, tick);
            }

            @Override
            public void preventDespawn(Mob mob) {
                // MockBukkit omits these native attribute registrations and despawn setter.
                mob.registerAttribute(Attribute.ATTACK_DAMAGE);
                mob.registerAttribute(Attribute.SPAWN_REINFORCEMENTS);
            }

            @Override
            public void disableEquipmentPickup(Mob defender) {
                // Unsupported MockBukkit setter; OpeningRosterProbe asserts the real Paper equipment policy.
            }

            @Override
            public void prepareRangedDefender(Mob defender) {
                // MockBukkit has no native navigation/goals; SecondWaveProbe checks dry pursuit and native beam.
            }

            @Override
            public void targetDefender(Mob defender, org.bukkit.entity.Player target) {
                // MockBukkit has no Brute/Breeze Brain; native arrival transitions are exercised by SecondWaveProbe.
                defender.setTarget(target);
            }
        };
        var context = new ModuleContext(plugin, config,
                new ConfigurateMessageService(data, getClass().getClassLoader(), "en", server),
                new ServiceRegistry(), scheduler, mock(CommandRegistrar.class), new EventBus(plugin));
        encounter = new EncounterController(server, battles, config.get(ShatteredCrownConfig.class), scheduler,
                context.messages());
        context.events().register(encounter);
        encounter.start();
        scheduler.advance(Duration.ZERO);
    }

    @AfterEach
    void stop() {
        encounter.close();
        MockBukkit.unmock();
    }

    @Test
    void naturalSixtyFivePercentGateInstallsSixMobsPerPlayerAcrossAllAnchors() {
        for (Mob defender : defenders()) {
            defeat(defender);
        }
        crystals().forEach(this::hit);
        var crossing = hit(dragon);
        assertThat(crossing.isCancelled()).isFalse();
        dragon.setHealth(dragon.getHealth() - crossing.getFinalDamage());
        scheduler.advance(Duration.ZERO);
        assertThat(encounter.waveNumber()).isEqualTo(2);
        assertThat(dragon.getHealth()).isEqualTo(650);
        assertThat(defenders()).hasSize(6);
        var groups = defenders().stream().collect(Collectors.groupingBy(encounter::anchorId));
        assertThat(groups).hasSize(4);
        assertThat(defenders().stream().collect(Collectors.groupingBy(Entity::getType, Collectors.counting())))
                .isEqualTo(UNIT);
        for (var group : groups.values()) {
            assertThat(group).hasSizeBetween(1, 2);
        }
        assertThat(hit(dragon).isCancelled()).isTrue();
        assertThat(encounter.unlockedAttacks()).doesNotContain(EncounterController.AttackFamily.RAIN);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 20, 68})
    void debugSnapshotsAllEndPlayersAndSpawnsSixPerPlayerWithoutACapOrLaterRescaling(int population) {
        for (int index = 1; index < population; index++) {
            var observer = server.addPlayer();
            observer.setGameMode(index % 2 == 0 ? org.bukkit.GameMode.CREATIVE : org.bukkit.GameMode.SPECTATOR);
            observer.teleport(new Location(end, 250, 64, 0));
        }
        encounter.debugWave(2, server.getConsoleSender());
        assertThat(encounter.wavePopulation()).isEqualTo(population);
        assertThat(encounter.rosterUnits()).isEqualTo(population);
        assertThat(defenders()).hasSize(6 * population);
        assertThat(defenders().stream().collect(Collectors.groupingBy(Entity::getType, Collectors.counting())))
                .containsExactlyInAnyOrderEntriesOf(UNIT.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> (long) population)));
        for (var crystal : crystals()) {
            var group = defenders().stream().filter(mob -> encounter.anchorId(mob).equals(crystal.getUniqueId()))
                    .toList();
            assertThat(group).hasSizeBetween(6 * population / 4, (6 * population + 3) / 4);
            for (var species : UNIT.keySet()) {
                assertThat(group.stream().filter(mob -> mob.getType() == species).count())
                        .isBetween((long) population / 4, (long) (population + 3) / 4);
            }
        }
        var roots = defenders();
        server.addPlayer().teleport(player.getLocation());
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(defenders()).containsExactlyInAnyOrderElementsOf(roots);
        assertThat(encounter.wavePopulation()).isEqualTo(population);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = EntityType.class,
            names = {"RAVAGER", "GUARDIAN", "PIGLIN_BRUTE", "VINDICATOR", "BLAZE", "BREEZE"})
    void eachSpeciesSurvivorHoldsItsOwnCrystalAndRainWaitsForAllFourCrystals(EntityType survivorType) {
        for (int index = 1; index < 4; index++) {
            server.addPlayer().teleport(player.getLocation());
        }
        encounter.debugWave(2, server.getConsoleSender());
        var objectives = crystals();
        for (Mob root : defenders()) {
            if (root.getType() != survivorType) {
                defeat(root);
            }
        }
        objectives.forEach(crystal -> assertThat(hit(crystal).isCancelled()).isTrue());
        assertThat(encounter.unlockedAttacks()).doesNotContain(EncounterController.AttackFamily.RAIN);
        for (var crystal : objectives) {
            var survivor = defenders().stream().filter(mob -> encounter.anchorId(mob).equals(crystal.getUniqueId()))
                    .findFirst().orElseThrow();
            defeat(survivor);
            assertThat(crystal.isGlowing()).isTrue();
            assertThat(encounter.unlockedAttacks()).doesNotContain(EncounterController.AttackFamily.RAIN);
            assertThat(hit(crystal).isCancelled()).isTrue(); // Owned objective removes itself, suppressing explosion.
            assertThat(crystal.isValid()).isFalse();
        }
        assertThat(encounter.unlockedAttacks()).contains(EncounterController.AttackFamily.RAIN);
        assertThat(hit(dragon).isCancelled()).isFalse();
    }

    @Test
    void missingSixSpeciesRootsAreReplacedWithTheirSpeciesTuningAndNotCountedAsDeaths() {
        encounter.debugWave(2, server.getConsoleSender());
        var objective = encounter.anchorId(defenders().getFirst());
        var missing = defenders().stream().filter(mob -> encounter.anchorId(mob).equals(objective)).toList();
        var expectedSpecies = missing.stream().collect(Collectors.groupingBy(Entity::getType, Collectors.counting()));
        missing.forEach(Entity::remove);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(defenders()).hasSize(6).doesNotContainAnyElementsOf(missing);
        var replacements = defenders().stream().filter(mob -> encounter.anchorId(mob).equals(objective)).toList();
        assertThat(replacements.stream().collect(Collectors.groupingBy(Entity::getType, Collectors.counting())))
                .isEqualTo(expectedSpecies);
        assertThat(replacements).allMatch(mob -> mob.getHealth() == 40);
        assertThat(hit(crystals().getFirst()).isCancelled()).isTrue();
        for (Mob root : replacements) {
            defeat(root);
            defeat(root); // Duplicate callbacks neither expose other Anchors nor replenish accepted deaths.
        }
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(defenders()).hasSize(6 - missing.size());
        assertThat(crystals().stream().filter(EnderCrystal::isGlowing)).hasSize(1);
    }

    @Test
    void allSixSpeciesAndRetiredAttributedEffectsRespectAlliesArrivalAndPublicCancellation() {
        server.addPlayer().teleport(player.getLocation());
        encounter.debugWave(2, server.getConsoleSender());
        var control = end.spawn(player.getLocation(), org.bukkit.entity.Enderman.class);
        var crystal = crystals().getFirst();
        var subjects = UNIT.keySet().stream().map(type -> defenders().stream().filter(mob -> mob.getType() == type)
                .findFirst().orElseThrow()).toList();
        for (Mob root : subjects) {
            var ally = defenders().stream().filter(mob -> mob != root).findFirst().orElseThrow();
            var retarget = new org.bukkit.event.entity.EntityTargetLivingEntityEvent(root, control,
                    org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY);
            server.getPluginManager().callEvent(retarget);
            assertThat(retarget.isCancelled()).isTrue();
            root.teleport(new Location(end, -150, 64, 0));
            scheduler.advance(Duration.ofSeconds(1));
            assertThat(root.getLocation().getX()).isEqualTo(-150); // No Anchor leash.
            assertThat(root.getTarget()).isSameAs(player);
            var shot = end.spawn(root.getLocation(), org.bukkit.entity.Arrow.class,
                    effect -> assertThat(encounter.inheritOwnership(effect, root)).isTrue());
            var source = DamageSource.builder(org.bukkit.damage.DamageType.ARROW).withDirectEntity(shot)
                    .withCausingEntity(root).build();
            for (Entity target : List.of(control, ally, dragon, crystal)) {
                var damage = new EntityDamageByEntityEvent(shot, target, EntityDamageEvent.DamageCause.PROJECTILE,
                        source, 10);
                server.getPluginManager().callEvent(damage);
                assertThat(damage.isCancelled()).isTrue();
            }
            player.teleport(new Location(end, 100, 50, 0));
            var arrival = new EntityDamageByEntityEvent(shot, player, EntityDamageEvent.DamageCause.PROJECTILE,
                    source, 10);
            server.getPluginManager().callEvent(arrival);
            assertThat(arrival.isCancelled()).isTrue();
            assertThat(root.getTarget()).isNull();
            player.teleport(new Location(end, 10, 64, 0));
            var canceled = new EntityDamageByEntityEvent(root, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    DamageSource.builder(org.bukkit.damage.DamageType.MOB_ATTACK).withDirectEntity(root)
                            .withCausingEntity(root).build(),
                    10);
            canceled.setCancelled(true);
            server.getPluginManager().callEvent(canceled);
            assertThat(canceled.isCancelled()).isTrue();
            defeat(root);
            var delayed = new EntityDamageByEntityEvent(shot, control, EntityDamageEvent.DamageCause.PROJECTILE,
                    source, 10);
            server.getPluginManager().callEvent(delayed);
            assertThat(delayed.isCancelled()).isTrue();
            assertThat(encounter.ownsCombatant(shot)).isTrue();
        }
        var effects = end.getEntitiesByClass(org.bukkit.entity.Arrow.class);
        encounter.debugWave(2, server.getConsoleSender());
        assertThat(effects).noneMatch(Entity::isValid).noneMatch(encounter::ownsCombatant);
        assertThat(defenders()).hasSize(12);
        assertThat(control.isValid()).isTrue();
        assertThat(end.getEntitiesByClass(org.bukkit.entity.PiglinBrute.class))
                .allMatch(org.bukkit.entity.PiglinBrute::isImmuneToZombification);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"reset", "disable", "unload"})
    void secondWaveTerminationRemovesAllRootsAndInheritedEffectsWithoutTouchingOrdinaryMobs(String termination) {
        encounter.debugWave(2, server.getConsoleSender());
        var roots = defenders();
        var ordinary = end.spawn(player.getLocation(), org.bukkit.entity.Guardian.class);
        var shot = end.spawn(player.getLocation(), org.bukkit.entity.Arrow.class,
                effect -> encounter.inheritOwnership(effect, roots.getFirst()));
        if (termination.equals("reset")) {
            player.teleport(new Location(end, 100, 50, 0));
            scheduler.advance(Duration.ofSeconds(60));
        } else if (termination.equals("unload")) {
            server.getPluginManager().callEvent(new org.bukkit.event.world.WorldUnloadEvent(end));
        } else {
            encounter.close();
        }
        assertThat(roots).noneMatch(Entity::isValid);
        assertThat(crystals()).isEmpty();
        assertThat(shot.isValid()).isFalse();
        assertThat(ordinary.isValid()).isTrue();
        assertThat(encounter.waveNumber()).isZero();
    }

    @Test
    void disabledRestartRecoveryRemovesAllSixPersistedSpeciesAndLateLoadedEffectsButPreservesOrdinaryMobs()
            throws Exception {
        encounter.close();
        var ordinary = end.spawn(player.getLocation(), org.bukkit.entity.Guardian.class);
        var stale = staleRoster();
        var plugin = MockBukkit.createMockPlugin("SecondWaveRecovery");
        var config = BasicConfigurationNode.root();
        config.node("enabled").set(false);
        var module = new ShatteredCrownModule();
        module.enable(new ModuleContext(plugin, config,
                new ConfigurateMessageService(data, getClass().getClassLoader(), "en", server),
                new ServiceRegistry(), scheduler, mock(CommandRegistrar.class), new EventBus(plugin)));
        assertThat(stale).noneMatch(Entity::isValid);
        var late = new java.util.ArrayList<>(staleRoster());
        var shot = end.spawn(player.getLocation(), org.bukkit.entity.Arrow.class);
        shot.getPersistentDataContainer().set(EncounterRecovery.ARTIFACT,
                org.bukkit.persistence.PersistentDataType.STRING, "defender-effect");
        late.add(shot);
        server.getPluginManager().callEvent(new org.bukkit.event.world.EntitiesLoadEvent(end.getChunkAt(0, 0), late));
        assertThat(late).noneMatch(Entity::isValid);
        assertThat(ordinary.isValid()).isTrue();
        assertThat(dragon.getHealth()).isEqualTo(200);
        assertThat(crystals()).isEmpty();
        module.disable();
    }

    private List<Entity> staleRoster() {
        return UNIT.keySet().stream().map(type -> {
            Entity entity = end.spawnEntity(player.getLocation(), type);
            entity.getPersistentDataContainer().set(EncounterRecovery.ARTIFACT,
                    org.bukkit.persistence.PersistentDataType.STRING, "defender");
            return entity;
        }).toList();
    }

    private List<Mob> defenders() {
        return end.getEntitiesByClass(Mob.class).stream().filter(Entity::isValid).filter(encounter::ownsCombatant)
                .toList();
    }

    private List<EnderCrystal> crystals() {
        return end.getEntitiesByClass(EnderCrystal.class).stream().filter(Entity::isValid).toList();
    }

    private EntityDamageEvent hit(Entity target) {
        var hit = new EntityDamageByEntityEvent(player, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                mock(DamageSource.class), 100000);
        server.getPluginManager().callEvent(hit);
        return hit;
    }

    private void defeat(Mob defender) {
        defender.setHealth(0);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDeathEvent(defender,
                mock(DamageSource.class), List.of()));
    }
}
