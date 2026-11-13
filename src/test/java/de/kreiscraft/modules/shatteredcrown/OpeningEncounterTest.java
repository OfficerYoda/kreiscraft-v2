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
    private int referenceHits;

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
                defender.registerAttribute(Attribute.FOLLOW_RANGE);
                defender.getAttribute(Attribute.FOLLOW_RANGE).addModifier(new org.bukkit.attribute.AttributeModifier(
                        new org.bukkit.NamespacedKey("test", "random_follow_range"), -0.1,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR));
            }

            @Override
            public void disableEquipmentPickup(Mob defender) {
                // Unsupported MockBukkit setter; OpeningRosterProbe asserts the real Paper equipment policy.
            }

            @Override
            public void faceWarningToViewer(org.bukkit.entity.TextDisplay warning) {
                // DisplayMock.setBillboard is unimplemented; location, text and lifetime remain real.
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

            @Override
            public void referenceDamage(org.bukkit.entity.Player recipient, EnderDragon source, double healthPoints) {
                referenceHits++;
                // Encounter policy only; native gear/difficulty mitigation is exercised by DragonDamageProbe.
                bombardmentDamage(recipient, source, healthPoints);
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
        config.node("opening-grace-seconds").set(0); // Isolate existing attack timing; grace has its own lifecycle test.
        config.node("movement-attacks").set(false); // Historical stationary bombardment/geometry seam, not new rotation.
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

    private List<Mob> defenders() {
        return end.getEntitiesByClass(Mob.class).stream().filter(Entity::isValid)
                .filter(mob -> mob != dragon && !(mob instanceof org.bukkit.entity.Enderman)).toList();
    }

    private EntityDamageEvent hit(Entity target) {
        var event = new EntityDamageByEntityEvent(player, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, mock(DamageSource.class), 10);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void defeat(Mob defender) {
        defender.setHealth(0);
        server.getPluginManager().callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
    }

    @Test
    void openingRosterDistributesMountedPairsLowerTowerAndIndividualUndead() throws Exception {
        enable();
        var owned = end.getEntitiesByClass(Mob.class).stream().filter(Entity::isValid)
                .filter(mob -> mob != dragon).toList();
        assertThat(owned).hasSize(16);
        assertThat(owned).allMatch(mob -> mob.getHealth() == mob.getAttribute(Attribute.MAX_HEALTH).getDefaultValue());
        assertThat(owned.stream().filter(mob -> mob.getVehicle() == null && !mob.getPassengers().isEmpty()))
                .hasSize(4);
        assertThat(owned.stream().map(Entity::getType)).contains(org.bukkit.entity.EntityType.HUSK,
                org.bukkit.entity.EntityType.DROWNED, org.bukkit.entity.EntityType.STRAY);
        for (var anchor : anchors()) {
            var roots = owned.stream().filter(mob -> mob.getVehicle() == null)
                    .filter(mob -> mob.getLocation().distance(anchor.getLocation()) < 5).toList();
            assertThat(owned.stream().filter(mob -> mob.getLocation().distance(anchor.getLocation()) < 5)).hasSize(4);
            for (var mount : roots) {
                if (mount.getPassengers().isEmpty()) {
                    continue;
                }
                assertThat(mount.getPassengers()).hasSize(1);
                Entity rider = mount.getPassengers().getFirst();
                switch (mount.getType()) {
                    case ZOMBIE_HORSE -> {
                        assertThat(rider.getType()).isEqualTo(org.bukkit.entity.EntityType.ZOMBIE);
                        assertThat(((Mob) rider).getEquipment().getItemInMainHand().getType())
                                .isEqualTo(Material.IRON_SPEAR);
                    }
                    case SPIDER -> assertThat(rider.getType()).isEqualTo(org.bukkit.entity.EntityType.SKELETON);
                    case CAVE_SPIDER -> assertThat(rider.getType()).isEqualTo(org.bukkit.entity.EntityType.BOGGED);
                    default -> {
                        int size = 1;
                        Entity current = mount;
                        while (!current.getPassengers().isEmpty()) {
                            current = current.getPassengers().getFirst();
                            size++;
                        }
                        assertThat(size).isEqualTo(3);
                        assertThat(current.getType()).as("native tower crown must be able to hit players below")
                                .isIn(org.bukkit.entity.EntityType.SKELETON, org.bukkit.entity.EntityType.STRAY,
                                        org.bukkit.entity.EntityType.BOGGED);
                    }
                }
            }
        }
    }

    @Test
    void openingArmorIsBalancedLightUnenchantedAndSurvivesMissingMemberRecovery() throws Exception {
        enable();
        var undead = end.getEntitiesByClass(Mob.class).stream().filter(Entity::isValid)
                .filter(mob -> mob instanceof Zombie || mob instanceof org.bukkit.entity.AbstractSkeleton).toList();
        assertThat(undead).hasSize(13);
        int armored = 0;
        for (var mob : undead) {
            var armor = java.util.Arrays.stream(mob.getEquipment().getArmorContents())
                    .filter(item -> item != null && !item.getType().isAir()).toList();
            if (!armor.isEmpty()) {
                armored++;
                assertThat(armor).hasSizeBetween(1, 2).allMatch(item -> item.getEnchantments().isEmpty()
                        && (item.getType().name().startsWith("LEATHER_")
                                || item.getType().name().startsWith("CHAINMAIL_")
                                || item.getType().name().startsWith("IRON_")));
            }
        }
        assertThat(armored).isEqualTo(5);
        var missing = undead.stream().filter(mob -> !mob.getEquipment().getHelmet().getType().isAir())
                .findFirst().orElseThrow();
        var armor = missing.getEquipment().getArmorContents();
        var before = end.getEntitiesByClass(Mob.class).stream().filter(Entity::isValid).toList();
        missing.remove();
        scheduler.advance(Duration.ofSeconds(1));
        var recovered = end.getEntitiesByClass(Mob.class).stream().filter(Entity::isValid)
                .filter(mob -> !before.contains(mob)).toList();
        assertThat(recovered).hasSize(1);
        assertThat(recovered.getFirst().getType()).isEqualTo(missing.getType());
        assertThat(recovered.getFirst().getEquipment().getArmorContents()).containsExactly(armor);
        assertThat(anchors()).allMatch(anchor -> !anchor.isGlowing());
    }

    @Test
    void brokenPassengerGroupsRemainFiniteAndEveryMountAndRiderStillGatesItsOriginalAnchor() throws Exception {
        enable();
        var horseRoot = defenders().stream().filter(mob -> mob.getType() == org.bukkit.entity.EntityType.ZOMBIE_HORSE)
                .findFirst().orElseThrow();
        var objective = anchors().stream().min(java.util.Comparator.comparingDouble(
                anchor -> anchor.getLocation().distanceSquared(horseRoot.getLocation()))).orElseThrow();
        var group =
                defenders().stream().filter(mob -> mob.getLocation().distance(objective.getLocation()) < 5).toList();
        var horse = group.stream().filter(mob -> mob.getType() == org.bukkit.entity.EntityType.ZOMBIE_HORSE)
                .findFirst().orElseThrow();
        var rider = (Mob) horse.getPassengers().getFirst();
        rider.leaveVehicle();
        rider.teleport(new Location(end, -120, 64, 0));
        for (var member : group) {
            if (member != horse) {
                defeat(member);
                server.getPluginManager().callEvent(new EntityDeathEvent(member, mock(DamageSource.class), List.of()));
            }
        }
        assertThat(objective.isGlowing()).isFalse();
        assertThat(hit(objective).isCancelled()).isTrue();
        int survivors = defenders().size();
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(defenders()).hasSize(survivors).contains(horse);
        defeat(horse);
        assertThat(objective.isGlowing()).isTrue();
        assertThat(anchors().stream().filter(anchor -> anchor != objective)).noneMatch(Entity::isGlowing);
        module.disable();
        assertThat(defenders()).isEmpty();
    }

    @Test
    void openingArmorTuningRejectsOutOfProfileValuesAndSnapshotsDefaults() throws Exception {
        assertThat(ShatteredCrownConfig.load(config).openingArmorPercentage()).isEqualTo(35);
        for (Object value : List.of(-1, 51, 35.5, "bad", Double.NaN)) {
            config.node("opening-armor-percentage").set(value);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> ShatteredCrownConfig.load(config))
                    .isInstanceOf(org.spongepowered.configurate.serialize.SerializationException.class);
        }
        config.node("opening-armor-percentage").set(0);
        enable();
        assertThat(defenders()).allSatisfy(mob -> assertThat(mob.getEquipment().getArmorContents())
                .allMatch(item -> item == null || item.getType().isAir()));
    }

    @Test
    void modeAndArrivalTransitionsDropTargetsImmediatelyAndRetargetEligibleAdventurePlayers() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(defenders()).allMatch(mob -> mob.getTarget() == player);
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        assertThat(defenders()).allMatch(mob -> mob.getTarget() == null);
        var teammate = server.addPlayer();
        teammate.setGameMode(org.bukkit.GameMode.ADVENTURE);
        teammate.teleport(new Location(end, -150, 64, 0));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(defenders()).allMatch(mob -> mob.getTarget() == teammate);
        teammate.teleport(new Location(end, 100, 50, 0));
        assertThat(defenders()).allMatch(mob -> mob.getTarget() == null);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(defenders()).allMatch(mob -> mob.getTarget() == player);
        player.setHealth(0);
        server.getPluginManager().callEvent(new EntityDeathEvent(player, mock(DamageSource.class), List.of()));
        assertThat(defenders()).allMatch(mob -> mob.getTarget() == null);
    }

    @Test
    void ownedMeleeProjectilesCloudsAndAttributedDelayedDamageCannotHurtAllies() throws Exception {
        enable();
        var mob = defenders().getFirst();
        var enderman = end.spawn(player.getLocation(), org.bukkit.entity.Enderman.class);
        var arrow = end.spawn(mob.getLocation(), org.bukkit.entity.Arrow.class);
        arrow.setShooter(mob);
        var cloud = end.spawn(mob.getLocation(), org.bukkit.entity.AreaEffectCloud.class);
        cloud.setSource(mob);
        for (Entity effect : List.of(mob, arrow, cloud)) {
            for (Entity ally : List.of(enderman, defenders().getLast(), dragon)) {
                var event = new EntityDamageByEntityEvent(effect, ally, EntityDamageEvent.DamageCause.MAGIC,
                        mock(DamageSource.class), 10);
                server.getPluginManager().callEvent(event);
                assertThat(event.isCancelled()).as("%s -> %s", effect.getType(), ally.getType()).isTrue();
            }
            var hitPlayer = new EntityDamageByEntityEvent(effect, player, EntityDamageEvent.DamageCause.MAGIC,
                    mock(DamageSource.class), 10);
            server.getPluginManager().callEvent(hitPlayer);
            assertThat(hitPlayer.isCancelled()).isFalse();
        }
        var delayedSource = DamageSource.builder(org.bukkit.damage.DamageType.EXPLOSION)
                .withDirectEntity(mob).withCausingEntity(mob).build();
        var delayed =
                new EntityDamageEvent(enderman, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, delayedSource, 10);
        server.getPluginManager().callEvent(delayed);
        assertThat(delayed.isCancelled()).isTrue();
        var applied = new org.bukkit.event.entity.AreaEffectCloudApplyEvent(cloud,
                new java.util.ArrayList<>(List.of(enderman, defenders().getLast(), dragon, player)));
        server.getPluginManager().callEvent(applied);
        assertThat(applied.getAffectedEntities()).containsExactly(player);
        var unrelated = end.spawn(player.getLocation(), Zombie.class);
        var normal = new EntityDamageByEntityEvent(unrelated, enderman, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                mock(DamageSource.class), 10);
        server.getPluginManager().callEvent(normal);
        assertThat(normal.isCancelled()).isFalse();
    }

    @Test
    void retaliationAndNativeRetargetingCannotChooseAlliesOrIneligiblePlayers() throws Exception {
        enable();
        var mob = defenders().getFirst();
        var enderman = end.spawn(player.getLocation(), org.bukkit.entity.Enderman.class);
        var observer = server.addPlayer();
        observer.teleport(player.getLocation());
        observer.setGameMode(org.bukkit.GameMode.CREATIVE);
        var arrival = server.addPlayer();
        arrival.teleport(new Location(end, 100, 50, 0));
        for (var reason : List.of(org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY,
                org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_ENTITY,
                org.bukkit.event.entity.EntityTargetEvent.TargetReason.REINFORCEMENT_TARGET)) {
            for (var ally : List.of(enderman, defenders().getLast(), dragon, observer, arrival)) {
                var targeting = new org.bukkit.event.entity.EntityTargetLivingEntityEvent(mob, ally, reason);
                server.getPluginManager().callEvent(targeting);
                assertThat(targeting.isCancelled()).as("%s -> %s", reason, ally.getType()).isTrue();
            }
            var permitted = new org.bukkit.event.entity.EntityTargetLivingEntityEvent(mob, player, reason);
            server.getPluginManager().callEvent(permitted);
            assertThat(permitted.isCancelled()).isFalse();
        }
        var ordinary = end.spawn(player.getLocation(), Zombie.class);
        var nativeTarget = new org.bukkit.event.entity.EntityTargetLivingEntityEvent(ordinary, enderman,
                org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY);
        server.getPluginManager().callEvent(nativeTarget);
        assertThat(nativeTarget.isCancelled()).isFalse();
    }

    @Test
    void defendersRoamTheWholeCylinderWithoutChangingTheirOriginalAnchorGate() throws Exception {
        enable();
        var objective = anchors().getFirst();
        var team = defenders().stream().filter(mob -> mob.getLocation().distance(objective.getLocation()) < 5).toList();
        var roaming = new Location(end, -150, end.getMaxHeight() - 1, 0);
        player.teleport(roaming);
        team.forEach(Entity::leaveVehicle);
        team.forEach(mob -> mob.teleport(roaming));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(team).allSatisfy(mob -> {
            assertThat(mob.getLocation()).isEqualTo(roaming);
            assertThat(mob.getTarget()).isEqualTo(player);
            assertThat(mob.getAttribute(Attribute.FOLLOW_RANGE).getValue()).isGreaterThanOrEqualTo(384);
        });
        hit(objective);
        assertThat(objective.isValid()).isTrue();
        team.forEach(this::defeat);
        assertThat(objective.isGlowing()).isTrue();
        assertThat(anchors().stream().filter(anchor -> anchor != objective)).noneMatch(Entity::isGlowing);
        hit(objective);
        assertThat(objective.isValid()).isFalse();
        assertThat(hit(dragon).isCancelled()).isTrue();
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
        assertThat(defenders()).hasSize(32);
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
    void bombardmentRemainsLockedThroughoutTheOpeningWave() throws Exception {
        enable();
        scheduler.advance(Duration.ofSeconds(25));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).isEmpty();
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(anchors()).hasSize(4);
        assertThat(hit(dragon).isCancelled()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"bombardment-warning-seconds,0", "bombardment-damage,NaN",
            "bombardment-radius,13"})
    void invalidBombardmentReloadsRetainThePlayableOpening(String key, String value) throws Exception {
        enable();
        var invalid = config.copy();
        invalid.node(key).set(value);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> module.onConfigReload(invalid))
                .isInstanceOf(RuntimeException.class);
        assertThat(anchors()).hasSize(4);
        scheduler.advance(Duration.ofSeconds(11));
        assertThat(player.getHealth()).isEqualTo(20);
    }

    @Test
    void anEmptyFourthColumnStillStartsFourIndependentlyDefendedObjectives() throws Exception {
        for (int x = -12; x <= 12; x++) {
            for (int z = -36; z <= -12; z++) {
                for (int y = end.getMinHeight(); y <= 63; y++) {
                    end.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
        enable();
        scheduler.advance(Duration.ofSeconds(11));
        assertThat(anchors()).hasSize(4);
        assertThat(defenders()).hasSize(16);
        assertThat(dragon.getHealth()).isEqualTo(1000);
    }

}
