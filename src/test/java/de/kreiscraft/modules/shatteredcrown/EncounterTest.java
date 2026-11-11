package de.kreiscraft.modules.shatteredcrown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
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

class EncounterTest {
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
    private org.spongepowered.configurate.ConfigurationNode config;

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
        // WorldMock.getEnderDragonBattle is unimplemented; entity/world behavior stays real.
        module = new ShatteredCrownModule(new DragonBattleAccess() {
            @Override
            public void targetDefender(org.bukkit.entity.Mob defender, org.bukkit.entity.Player target) {
                defender.setTarget(target); // Native Warden brain/sonic is exercised by FinalWaveProbe.
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
            public void preventDespawn(org.bukkit.entity.Mob defender) {
                // LivingEntityMock.setRemoveWhenFarAway is unimplemented. Recovery is tested via real removal.
                // MockBukkit's husk attributes also omit the vanilla attack-damage attribute.
                defender.registerAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
                defender.registerAttribute(org.bukkit.attribute.Attribute.SPAWN_REINFORCEMENTS);
                defender.getAttribute(org.bukkit.attribute.Attribute.SPAWN_REINFORCEMENTS).setBaseValue(0.1);
            }

            @Override
            public void faceWarningToViewer(org.bukkit.entity.TextDisplay warning) {
                // DisplayMock.setBillboard is unimplemented; all warning entity effects remain real.
            }

            @Override
            public void bombardmentDamage(org.bukkit.entity.Player recipient, EnderDragon source, double amount) {
                // LivingEntityMock.damage(DamageSource) is unimplemented; use MockBukkit's existing simulation.
                new org.mockbukkit.mockbukkit.simulate.entity.LivingEntitySimulation((PlayerMock) recipient)
                        .simulateDamage(amount, DamageSource.builder(org.bukkit.damage.DamageType.DRAGON_BREATH)
                                .withCausingEntity(source).build());
            }

            @Override
            public void referenceDamage(org.bukkit.entity.Player recipient, EnderDragon source, double healthPoints) {
                // Encounter policy only; DragonDamageProbe verifies native reference-gear mitigation.
                bombardmentDamage(recipient, source, healthPoints);
            }
        });
    }

    @AfterEach
    void tearDown() {
        module.disable();
        MockBukkit.unmock();
    }

    @Test
    void participantsDefeatDefendersThenAnchorAndCanFinishTheNormalDragonBattle() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).hasSize(4);
        assertThat(defenders()).hasSize(16);
        EnderCrystal anchor = crystals().getFirst();
        assertThat(hit(dragon).isCancelled()).isTrue();
        assertThat(hit(anchor).isCancelled()).isTrue();
        assertThat(anchor.isValid()).isTrue();

        for (org.bukkit.entity.Mob defender : defenders()) {
            defender.setHealth(0);
            server.getPluginManager().callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
        }
        assertThat(anchor.isGlowing()).isTrue();
        hit(anchor);
        assertThat(anchor.isValid()).isFalse();
        crystals().forEach(this::hit);
        assertThat(hit(dragon).isCancelled()).isFalse();
        clearFinalGate();
        server.getPluginManager().callEvent(new EntityDeathEvent(dragon, mock(DamageSource.class), List.of(), 12000));
        scheduler.advance(Duration.ofSeconds(65));
        assertThat(crystals()).isEmpty();
        assertThat(defenders()).isEmpty();
        assertThat(dragon.isValid()).isTrue(); // Completion remains owned by vanilla, never remove/kill it.
    }

    @Test
    void ownedPotionEffectsCannotReachArrivalsAndCombatProtectionDoesNotProtectTerrain() throws Exception {
        enable();
        scheduler.advance(Duration.ZERO);
        var defender = defenders().getFirst();
        player.teleport(new Location(end, 100, 50, 0));
        var potion = end.spawn(defender.getLocation(), org.bukkit.entity.ThrownPotion.class);
        potion.setShooter(defender);
        var splash = new org.bukkit.event.entity.PotionSplashEvent(potion,
                new java.util.HashMap<>(java.util.Map.of(player, 1.0)));
        server.getPluginManager().callEvent(splash);
        assertThat(splash.getIntensity(player)).isZero();
        var cloud = end.spawn(defender.getLocation(), org.bukkit.entity.AreaEffectCloud.class);
        cloud.setSource(defender);
        var affected = new java.util.ArrayList<org.bukkit.entity.LivingEntity>(List.of(player));
        var applied = new org.bukkit.event.entity.AreaEffectCloudApplyEvent(cloud, affected);
        server.getPluginManager().callEvent(applied);
        assertThat(applied.getAffectedEntities()).isEmpty();
        var block = player.getLocation().getBlock();
        block.setType(Material.OBSIDIAN);
        var explosion = new org.bukkit.event.entity.EntityExplodeEvent(defender, player.getLocation(),
                new java.util.ArrayList<>(List.of(block)), 1, org.bukkit.ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(explosion);
        assertThat(explosion.isCancelled()).isFalse();
        assertThat(explosion.blockList()).containsExactly(block);
    }

    @Test
    void ownedDefenderProjectilesAndAttributedCloudsCannotHitArrivalsButUnrelatedProjectilesRemainNative()
            throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        var defender = defenders().getFirst();
        var arrow = end.spawn(defender.getLocation(), org.bukkit.entity.Arrow.class);
        arrow.setShooter(defender);
        player.teleport(new Location(end, 100, 50, 0));
        var incoming = new EntityDamageByEntityEvent(arrow, player, EntityDamageEvent.DamageCause.PROJECTILE,
                DamageSource.builder(org.bukkit.damage.DamageType.ARROW).withDirectEntity(arrow)
                        .withCausingEntity(defender).build(),
                10);
        server.getPluginManager().callEvent(incoming);
        assertThat(incoming.isCancelled()).isTrue();
        var cloud = end.spawn(defender.getLocation(), org.bukkit.entity.AreaEffectCloud.class);
        cloud.setSource(defender);
        var indirect = new EntityDamageByEntityEvent(cloud, player, EntityDamageEvent.DamageCause.MAGIC,
                DamageSource.builder(org.bukkit.damage.DamageType.MAGIC).withDirectEntity(cloud).build(), 10);
        server.getPluginManager().callEvent(indirect);
        assertThat(indirect.isCancelled()).isTrue();
        var outsider = server.addPlayer();
        var unrelated = end.spawn(defender.getLocation(), org.bukkit.entity.Arrow.class);
        unrelated.setShooter(outsider);
        var nativeHit = new EntityDamageByEntityEvent(unrelated, player, EntityDamageEvent.DamageCause.PROJECTILE,
                DamageSource.builder(org.bukkit.damage.DamageType.ARROW).withDirectEntity(unrelated)
                        .withCausingEntity(outsider).build(),
                10);
        server.getPluginManager().callEvent(nativeHit);
        assertThat(nativeHit.isCancelled()).isFalse();
    }

    private void enable() throws Exception {
        enable(true);
    }

    private void clearFinalGate() {
        for (int stage = 0; stage < 3; stage++) {
            for (org.bukkit.entity.Mob defender : defenders()) {
                defender.setHealth(0);
                server.getPluginManager()
                        .callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
            }
            crystals().forEach(this::hit);
            if (stage < 2) {
                dragon.setHealth(stage == 0 ? 660 : 310);
                var crossing = hit(dragon);
                assertThat(crossing.isCancelled()).isFalse();
                dragon.setHealth(dragon.getHealth() - crossing.getFinalDamage());
                scheduler.advance(Duration.ZERO);
            }
        }
    }

    private void enable(boolean enabled) throws Exception {
        config = BasicConfigurationNode.root();
        config.node("world-key").set(end.getKey().toString());
        config.node("enabled").set(enabled);
        module.enable(new ModuleContext(plugin, config,
                new ConfigurateMessageService(data, getClass().getClassLoader(), "en", server,
                        recipient -> recipient.getName().equals("Deutsch") ? java.util.Optional.of("de")
                                : java.util.Optional.empty()),
                new ServiceRegistry(), scheduler, mock(CommandRegistrar.class), new EventBus(plugin)));
    }

    @Test
    void disabledModuleLeavesTheBattleAndOtherEntitiesUntouched() throws Exception {
        enable(false);
        scheduler.advance(Duration.ofSeconds(120));
        assertThat(crystals()).isEmpty();
        assertThat(defenders()).isEmpty();
        assertThat(dragon.getHealth()).isEqualTo(200);
        assertThat(hit(dragon).isCancelled()).isFalse();
    }

    @Test
    void emptyColumnsUseFloatingObjectivesWithoutRebuildingTerrain() throws Exception {
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                for (int y = end.getMinHeight(); y <= 63; y++) {
                    end.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).hasSize(4).allMatch(crystal -> crystal.getY() == 64);
        assertThat(defenders()).hasSize(16);
        assertThat(dragon.getHealth()).isEqualTo(1000);
        assertThat(hit(dragon).isCancelled()).isTrue();
    }

    @Test
    void openingExposureAndShieldReleaseStayQuietForBothLanguages() throws Exception {
        PlayerMock german = server.addPlayer("Deutsch");
        german.teleport(player.getLocation());
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).hasSize(4);
        var bossBar = dragon.getDragonBattle().getBossBar();
        for (org.bukkit.entity.Mob defender : defenders()) {
            defender.setHealth(0);
            server.getPluginManager().callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
        }
        assertThat(crystals()).allMatch(EnderCrystal::isGlowing);
        crystals().forEach(this::hit);
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(hit(dragon).isCancelled()).isFalse();
        assertThat(dragon.getDragonBattle().getBossBar()).isSameAs(bossBar);
        for (PlayerMock recipient : List.of(player, german)) {
            assertThat(recipient.nextMessage()).isNull();
            assertThat(recipient.nextActionBar()).isNull();
        }
    }

    @Test
    void onlyLivingCombatParticipantsStartOneEncounterInTheDesignatedEnd() throws Exception {
        player.setGameMode(GameMode.SPECTATOR);
        enable();
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(crystals()).isEmpty();
        player.setGameMode(GameMode.CREATIVE);
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(crystals()).isEmpty();
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(crystals()).isEmpty();
        player.teleport(new Location(end, 200, 64, 0));
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(crystals()).isEmpty();
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(new Location(end, 10, 64, 0));
        scheduler.advance(Duration.ofSeconds(2));
        EnderCrystal anchor = crystals().getFirst();
        assertThat(anchor.getLocation().getBlock().getRelative(0, -1, 0).getType().isSolid()).isTrue();
        assertThat(anchor.getLocation().getY()).isEqualTo(64);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntitySpawnEvent(dragon));
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(crystals()).hasSize(4).contains(anchor);
        assertThat(dragon.getHealth()).isEqualTo(1000);
    }

    private EntityDamageEvent hit(Entity target) {
        var event = new EntityDamageByEntityEvent(player, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, mock(DamageSource.class), 10);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void returningBeforeSixtySecondsPreservesProgressButContinuousAbsenceResetsForRetry() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        EnderCrystal first = crystals().getFirst();
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(59));
        assertThat(crystals()).hasSize(4).contains(first);
        player.teleport(new Location(end, 10, 64, 0));
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(crystals()).hasSize(4).contains(first);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofMillis(59999));
        assertThat(crystals()).hasSize(4).contains(first);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(crystals()).isEmpty();
        assertThat(defenders()).isEmpty();
        assertThat(dragon.getHealth()).isEqualTo(200);
        assertThat(dragon.isValid()).isTrue();
        player.teleport(new Location(end, 10, 64, 0));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).hasSize(4).doesNotContain(first);
        assertThat(defenders()).hasSize(16);
        assertThat(hit(dragon).isCancelled()).isTrue();
    }

    private List<EnderCrystal> crystals() {
        return end.getEntitiesByClass(EnderCrystal.class).stream().filter(Entity::isValid).toList();
    }

    @Test
    void shieldsCoverAllDamageRoutesAndProtectedShotsCannotBypassTheArrivalBoundary() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        for (var cause : List.of(EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                EntityDamageEvent.DamageCause.PROJECTILE, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
            var damage = new EntityDamageEvent(dragon, cause, mock(DamageSource.class), 5000);
            server.getPluginManager().callEvent(damage);
            assertThat(damage.isCancelled()).isTrue();
        }
        // EnderDragonMock.getParts is unimplemented: only the Paper multipart envelope is doubled.
        var part = mock(org.bukkit.entity.ComplexEntityPart.class);
        when(part.getParent()).thenReturn(dragon);
        assertThat(hit(part).isCancelled()).isTrue();
        var healing = new org.bukkit.event.entity.EntityRegainHealthEvent(dragon, 10,
                org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.ENDER_CRYSTAL);
        server.getPluginManager().callEvent(healing);
        assertThat(healing.isCancelled()).isTrue();
        player.teleport(new Location(end, 100, 50, 0));
        assertThat(hit(defenders().getFirst()).isCancelled()).isTrue();
        var arrow = end.spawn(player.getLocation(), org.bukkit.entity.Arrow.class);
        arrow.setShooter(player);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.ProjectileLaunchEvent(arrow));
        player.teleport(new Location(end, 10, 64, 0));
        arrow.teleport(new Location(end, 25, 64, 0));
        var shot = new EntityDamageByEntityEvent(arrow, defenders().getFirst(),
                EntityDamageEvent.DamageCause.PROJECTILE, mock(DamageSource.class), 20);
        server.getPluginManager().callEvent(shot);
        assertThat(shot.isCancelled()).isTrue();
        var ordinary = end.spawn(new Location(end, 50, 65, 0), EnderCrystal.class);
        assertThat(hit(ordinary).isCancelled()).isFalse();
        assertThat(hit(dragon).isCancelled()).isTrue();
    }

    private List<org.bukkit.entity.Mob> defenders() {
        return end.getEntitiesByClass(org.bukkit.entity.Mob.class).stream().filter(mob -> mob != dragon)
                .filter(Entity::isValid).toList();
    }

    @Test
    void protectedArrivalsCannotKillTheIdleManagedDragonWithoutStartingAnEncounter() throws Exception {
        player.teleport(new Location(end, 100, 50, 0));
        enable();
        assertThat(hit(dragon).isCancelled()).isTrue();
        assertThat(crystals()).isEmpty();
        assertThat(dragon.getHealth()).isEqualTo(200);
    }

    @Test
    void aHitBeforeTheFirstDiscoveryTickCannotSkipTheOpeningShield() throws Exception {
        enable();
        assertThat(hit(dragon).isCancelled()).isTrue();
        assertThat(crystals()).hasSize(4);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).hasSize(4);
        assertThat(defenders()).hasSize(16);
    }

    @Test
    void aShotFromThePlatformBeforeTheEncounterStartsStillCannotDamageItsDefenders() throws Exception {
        player.teleport(new Location(end, 100, 50, 0));
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).isEmpty();
        var arrow = end.spawn(player.getLocation(), org.bukkit.entity.Arrow.class);
        arrow.setShooter(player);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.ProjectileLaunchEvent(arrow));
        player.teleport(new Location(end, 10, 64, 0));
        scheduler.advance(Duration.ofSeconds(1));
        arrow.teleport(crystals().getFirst().getLocation());
        var damage = new EntityDamageByEntityEvent(arrow, defenders().getFirst(),
                EntityDamageEvent.DamageCause.PROJECTILE, mock(DamageSource.class), 20);
        server.getPluginManager().callEvent(damage);
        assertThat(damage.isCancelled()).isTrue();
    }

    @Test
    void defenderWavesCannotGrowThroughReinforcementsOrEscapeOwnershipByTransforming() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        var defender = defenders().getFirst();
        assertThat(defender.getAttribute(org.bukkit.attribute.Attribute.SPAWN_REINFORCEMENTS).getValue()).isZero();
        var replacement = end.spawn(new Location(end, 50, 64, 0), org.bukkit.entity.Zombie.class);
        var transform = new org.bukkit.event.entity.EntityTransformEvent(defender, List.of(replacement),
                org.bukkit.event.entity.EntityTransformEvent.TransformReason.DROWNED);
        server.getPluginManager().callEvent(transform);
        assertThat(transform.isCancelled()).isTrue();
    }

    @Test
    void movingPrimedTntOutOfArrivalProtectionDoesNotTurnItIntoSafeDamage() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        player.teleport(new Location(end, 100, 50, 0));
        var tnt = end.spawn(player.getLocation(), org.bukkit.entity.TNTPrimed.class);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntitySpawnEvent(tnt));
        player.teleport(new Location(end, 10, 64, 0));
        tnt.teleport(new Location(end, 25, 64, 0));
        var damage = new EntityDamageByEntityEvent(tnt, defenders().getFirst(),
                EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, mock(DamageSource.class), 20);
        server.getPluginManager().callEvent(damage);
        assertThat(damage.isCancelled()).isTrue();
    }

    @Test
    void aLaterVanillaRitualDragonGetsOneFreshEncounterAndOtherWorldsRemainUnaffected() throws Exception {
        var other = server.addSimpleWorld("unmanaged-end");
        other.setEnvironment(World.Environment.THE_END);
        var unrelated = other.spawn(new Location(other, 0, 80, 0), EnderDragon.class);
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(hit(unrelated).isCancelled()).isFalse();
        assertThat(other.getEntitiesByClass(EnderCrystal.class)).isEmpty();
        clearFinalGate();
        server.getPluginManager().callEvent(new EntityDeathEvent(dragon, mock(DamageSource.class), List.of(), 12000));
        dragon.remove();
        dragon = end.spawn(new Location(end, 0, 80, 0), EnderDragon.class);
        dragon.setPodium(new Location(end, 0, 64, 0));
        dragon.getDragonBattle().setPreviouslyKilled(true);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntitySpawnEvent(dragon));
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntitySpawnEvent(dragon));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(crystals()).hasSize(4);
        assertThat(defenders()).hasSize(16);
        assertThat(end.getEntitiesByClass(EnderDragon.class)).containsExactly(dragon);
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isTrue();
        assertThat(hit(dragon).isCancelled()).isTrue();
    }

    @Test
    void repeatedAttemptsWithOnlyDeadPlayersOrSpectatorsDoNotAccumulateArtifacts() throws Exception {
        var spectator = server.addPlayer();
        spectator.teleport(player.getLocation());
        spectator.setGameMode(GameMode.SPECTATOR);
        enable();
        for (int attempt = 0; attempt < 8; attempt++) {
            player.teleport(new Location(end, 10, 64, 0));
            scheduler.advance(Duration.ofSeconds(1));
            assertThat(crystals()).hasSize(4);
            assertThat(defenders()).hasSize(new int[] {16, 16, 16, 32, 32, 32, 32, 48}[attempt]);
            player.setHealth(0);
            scheduler.advance(Duration.ofSeconds(60));
            assertThat(crystals()).isEmpty();
            assertThat(defenders()).isEmpty();
            player = server.addPlayer();
        }
        module.disable();
        scheduler.advance(Duration.ofSeconds(120));
        assertThat(end.getEntitiesByClass(EnderDragon.class)).containsExactly(dragon);
        assertThat(crystals()).isEmpty();
    }

    @Test
    void refusedDefenderSpawnsAbortCleanlyInsteadOfLeavingAnImpossibleShield() throws Exception {
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void veto(org.bukkit.event.entity.CreatureSpawnEvent event) {
                if (event.getEntity() instanceof Zombie) {
                    event.setCancelled(true);
                }
            }
        }, plugin);
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).isEmpty();
        assertThat(defenders()).isEmpty();
        assertThat(hit(dragon).isCancelled()).isFalse();
        assertThat(dragon.getHealth()).isEqualTo(200);
    }

    @Test
    void encounterCrystalsCannotIgniteTerrainButOrdinaryCrystalsKeepVanillaBehavior() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        var anchor = crystals().getFirst();
        var ignition = new org.bukkit.event.block.BlockIgniteEvent(anchor.getLocation().getBlock(),
                org.bukkit.event.block.BlockIgniteEvent.IgniteCause.ENDER_CRYSTAL, anchor);
        server.getPluginManager().callEvent(ignition);
        assertThat(ignition.isCancelled()).isTrue();
        var ordinary = end.spawn(new Location(end, 50, 65, 0), EnderCrystal.class);
        var normal = new org.bukkit.event.block.BlockIgniteEvent(ordinary.getLocation().getBlock(),
                org.bukkit.event.block.BlockIgniteEvent.IgniteCause.ENDER_CRYSTAL, ordinary);
        server.getPluginManager().callEvent(normal);
        assertThat(normal.isCancelled()).isFalse();
    }

    @Test
    void cancelledDeathAndDamageDoNotAdvanceTheObjectiveAndVanillaRewardsAreUnmodified() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        for (org.bukkit.entity.Mob defender : defenders()) {
            var cancelled = new EntityDeathEvent(defender, mock(DamageSource.class), List.of());
            cancelled.setCancelled(true);
            server.getPluginManager().callEvent(cancelled);
        }
        assertThat(crystals().getFirst().isGlowing()).isFalse();
        for (org.bukkit.entity.Mob defender : defenders()) {
            defender.setHealth(0);
            server.getPluginManager().callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
        }
        var cancelled = new EntityDamageByEntityEvent(player, crystals().getFirst(),
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, mock(DamageSource.class), 10);
        cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        assertThat(crystals()).hasSize(4);
        crystals().forEach(this::hit);
        clearFinalGate();
        var death = new EntityDeathEvent(dragon, mock(DamageSource.class), List.of(), 12000);
        server.getPluginManager().callEvent(death);
        assertThat(death.isCancelled()).isFalse();
        assertThat(death.getDroppedExp()).isEqualTo(12000);
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isFalse();
        assertThat(crystals()).isEmpty();
    }

    @Test
    void anUnloadedAnchorIsRecreatedWithoutLosingItsDefenderGate() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        EnderCrystal original = crystals().getFirst();
        original.remove();
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).hasSize(4).doesNotContain(original);
        assertThat(defenders()).hasSize(16);
        assertThat(hit(crystals().getFirst()).isCancelled()).isTrue();
        assertThat(crystals()).hasSize(4);
        assertThat(hit(dragon).isCancelled()).isTrue();
    }

    @Test
    void restartOwnershipRecoveryRemovesStaleArtifactsAndRestoresTheExistingDragonWithoutRewards() throws Exception {
        var stale = end.spawn(new Location(end, 24, 64, 0), EnderCrystal.class);
        stale.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("kreiscraft", "crown_artifact"),
                org.bukkit.persistence.PersistentDataType.STRING, "anchor");
        var ordinary = end.spawn(new Location(end, 50, 64, 0), EnderCrystal.class);
        dragon.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("kreiscraft", "crown_original_health"),
                org.bukkit.persistence.PersistentDataType.DOUBLE, 200.0);
        dragon.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(1200);
        dragon.setHealth(123);
        player.setGameMode(GameMode.SPECTATOR);
        enable();
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(crystals()).containsExactly(ordinary);
        assertThat(dragon.getHealth()).isEqualTo(200);
        assertThat(dragon.getDragonBattle().hasBeenPreviouslyKilled()).isFalse();
        player.setGameMode(GameMode.SURVIVAL);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).hasSize(5);
        assertThat(end.getEntitiesByClass(EnderDragon.class)).containsExactly(dragon);
    }

    @Test
    void worldUnloadCancelsTheAttemptAndLoadingTheBattleAgainStartsFresh() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        EnderCrystal first = crystals().getFirst();
        player.teleport(new Location(end, 100, 50, 0));
        server.getPluginManager().callEvent(new org.bukkit.event.world.WorldUnloadEvent(end));
        assertThat(crystals()).isEmpty();
        assertThat(defenders()).isEmpty();
        assertThat(hit(dragon).isCancelled()).isFalse();
        player.teleport(new Location(end, 10, 64, 0));
        scheduler.advance(Duration.ofSeconds(65));
        assertThat(crystals()).isEmpty();
        server.getPluginManager().callEvent(new org.bukkit.event.world.WorldLoadEvent(end));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(crystals()).hasSize(4).doesNotContain(first);
    }

    @Test
    void reloadValidatesAtomicallyAndAppliesTuningOnlyToTheNextAttemptWhileDisableAbortsNow() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        var invalid = config.copy();
        invalid.node("max-health").set(-1);
        assertThatThrownBy(() -> module.onConfigReload(invalid)).isInstanceOf(RuntimeException.class);
        invalid.node("max-health").set(Double.NaN);
        assertThatThrownBy(() -> module.onConfigReload(invalid)).isInstanceOf(RuntimeException.class);
        invalid.node("max-health").set(1025);
        assertThatThrownBy(() -> module.onConfigReload(invalid)).isInstanceOf(RuntimeException.class);
        var next = config.copy();
        next.node("max-health").set(800);
        next.node("defender-count").set(6);
        module.onConfigReload(next);
        assertThat(dragon.getHealth()).isEqualTo(1000);
        assertThat(defenders()).hasSize(16);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(60));
        player.teleport(new Location(end, 10, 64, 0));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(dragon.getHealth()).isEqualTo(800);
        assertThat(defenders()).hasSize(24);
        next.node("enabled").set(false);
        module.onConfigReload(next);
        assertThat(defenders()).isEmpty();
        assertThat(crystals()).isEmpty();
        assertThat(hit(dragon).isCancelled()).isFalse();
        scheduler.advance(Duration.ofSeconds(120));
        assertThat(crystals()).isEmpty();
    }

    @Test
    void escapedAndUnloadedDefendersRemainDefeatableAndDisableRemovesOnlyOwnedArtifacts() throws Exception {
        var unrelated = end.spawn(new Location(end, 50, 64, 0), Zombie.class);
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        var owned = defenders().stream().filter(entity -> entity != unrelated).toList();
        assertThat(owned).hasSize(16);
        var home = owned.getFirst().getLocation();
        owned.getFirst().teleport(new Location(end, 200, 10, 0));
        owned.get(1).remove();
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(owned.getFirst().getLocation().distance(home)).isLessThan(5);
        assertThat(defenders()).hasSize(17);
        module.disable();
        module.disable();
        scheduler.advance(Duration.ofSeconds(120));
        assertThat(defenders()).containsExactly(unrelated);
        assertThat(crystals()).isEmpty();
        assertThat(hit(dragon).isCancelled()).isFalse();
        assertThat(dragon.getHealth()).isEqualTo(200);
    }
}
