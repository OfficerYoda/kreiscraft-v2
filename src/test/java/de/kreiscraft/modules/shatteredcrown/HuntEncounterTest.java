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
    private Runnable beforeNativeRelease;
    private CommandRegistrar commands;
    private com.mojang.brigadier.tree.LiteralCommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> debugCommand;
    private int referenceHits;
    private String language = "en";
    private java.util.function.Consumer<DamageSource> deathOutcome;

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
        commands = mock(CommandRegistrar.class);
        org.mockito.Mockito.doAnswer(call -> {
            debugCommand = call.getArgument(0);
            return null;
        }).when(commands).register(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        module = new ShatteredCrownModule(new DragonBattleAccess() {
            @Override
            public de.kreiscraft.platform.DragonFlightControl controlFlight(EnderDragon source, Runnable tick) {
                var nativeControl = new SimulatedDragonFlight(source, scheduler, tick);
                return new de.kreiscraft.platform.DragonFlightControl() {
                    @Override
                    public boolean active() {
                        return nativeControl.active();
                    }

                    @Override
                    public void move(Location destination) {
                        if (source.getLocation().distanceSquared(destination) > 64) {
                            throw new IllegalArgumentException("Scripted dragon step exceeds eight blocks");
                        }
                        nativeControl.move(destination);
                    }

                    @Override
                    public void close() {
                        try {
                            if (beforeNativeRelease != null) {
                                beforeNativeRelease.run();
                            }
                        } finally {
                            nativeControl.close();
                        }
                    }
                };
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
            public void faceWarningToViewer(TextDisplay warning) {
                // Only the unsupported billboard setter is omitted.
            }

            @Override
            public void prepareRainFireball(org.bukkit.entity.LargeFireball fireball, double speed) {
                // Only native drag/acceleration balancing is absent from MockBukkit; RainProbe checks actual descent.
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
            public void bombardmentDamage(org.bukkit.entity.Player recipient, EnderDragon source, double amount) {
                DamageSource damageSource = DamageSource.builder(DamageType.DRAGON_BREATH)
                        .withDirectEntity(source).withCausingEntity(source).build();
                if (deathOutcome != null) {
                    // MockBukkit replaces lethal non-player sources with GENERIC; supply Paper's sourced death event.
                    deathOutcome.accept(damageSource);
                    return;
                }
                new org.mockbukkit.mockbukkit.simulate.entity.LivingEntitySimulation((PlayerMock) recipient)
                        .simulateDamage(amount, damageSource);
            }

            @Override
            public void referenceDamage(org.bukkit.entity.Player recipient, EnderDragon source, double healthPoints) {
                referenceHits++;
                // Encounter policy only; DragonDamageProbe verifies native reference-gear mitigation.
                bombardmentDamage(recipient, source, healthPoints);
            }
        });
        config = BasicConfigurationNode.root();
        config.node("opening-grace-seconds").set(0); // Isolate attack timing from the separately tested opening grace.
        config.node("movement-attacks").set(false); // Legacy stationary-hazard contract.
        config.node("prey-percentage").set(100); // Legacy multi-target geometry cases mark every participant.
        config.node("pool-radius").set(3); // Those fixtures retain their original escape/boundary dimensions.
        // Lane-focused scenarios defer the newly coordinated attacks; dedicated cases set their cadences.
        config.node("prey-cadence-seconds").set(120);
        config.node("sweep-cadence-seconds").set(120);
    }

    private List<org.bukkit.entity.Mob> defenders() {
        return end.getEntitiesByClass(org.bukkit.entity.Mob.class).stream().filter(org.bukkit.entity.Entity::isValid)
                .filter(mob -> mob != dragon && !(mob instanceof org.bukkit.entity.Enderman)).toList();
    }

    @AfterEach
    void tearDown() {
        module.disable();
        MockBukkit.unmock();
        // Fault-injected static rendering calls must not retain prior mock worlds between scenarios.
        org.mockito.Mockito.framework().clearInlineMocks();
    }

    private void hunt() throws Exception {
        startEncounter();
        defenders().forEach(defender -> {
            defender.setHealth(0);
            server.getPluginManager().callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
        });
        end.getEntitiesByClass(EnderCrystal.class).forEach(this::hit);
    }

    private void startEncounter() throws Exception {
        if (!config.node("movement-attacks").getBoolean(true)) {
            // Explicit historical sector tuning; native Smash uses the redesigned defaults.
            if (config.node("sweep-warning-seconds").virtual()) {
                config.node("sweep-warning-seconds").set(4);
            }
            if (config.node("sweep-radius").virtual()) {
                config.node("sweep-radius").set(12);
            }
            if (config.node("sweep-damage").virtual()) {
                config.node("sweep-damage").set(6);
            }
            if (config.node("lane-warning-seconds").virtual()) {
                config.node("lane-warning-seconds").set(3);
            }
            if (config.node("lane-damage").virtual()) {
                config.node("lane-damage").set(4);
            }
        }
        config.node("enabled").set(true);
        config.node("world-key").set(end.getKey().toString());
        module.enable(new ModuleContext(plugin, config,
                new ConfigurateMessageService(data, getClass().getClassLoader(), language, server,
                        recipient -> recipient.getName().equals("Deutsch") ? java.util.Optional.of("de")
                                : java.util.Optional.empty()),
                new ServiceRegistry(), scheduler, commands, new EventBus(plugin)));
        scheduler.advance(Duration.ZERO);
    }

    private int debug(String command, org.bukkit.command.CommandSender sender) throws Exception {
        var dispatcher =
                new com.mojang.brigadier.CommandDispatcher<io.papermc.paper.command.brigadier.CommandSourceStack>();
        dispatcher.getRoot().addChild(debugCommand);
        var source = mock(io.papermc.paper.command.brigadier.CommandSourceStack.class);
        org.mockito.Mockito.when(source.getSender()).thenReturn(sender);
        return dispatcher.execute("dragondebug " + command, source);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
            "en|bombardment| was reduced to ashes by the Ender Dragon's bombardment.",
            "en|prey| became the Ender Dragon's next meal.",
            "en|dive| stood in the Ender Dragon's flight path.",
            "en|fireball-rain| discovered that dragon fire falls harder than rain.",
            "en|smash| was crushed by the Ender Dragon.",
            "de|bombardment| wurde vom Bombardement des Enderdrachen zu Asche verbrannt.",
            "de|prey| wurde zur nächsten Mahlzeit des Enderdrachen.",
            "de|dive| stand dem Enderdrachen im Weg.",
            "de|fireball-rain| lernte, dass Drachenfeuer härter fällt als Regen.",
            "de|smash| wurde vom Enderdrachen zerschmettert."})
    void fatalAttacksUseTheirOwnDeathMessageInBothLanguages(String locale, String attack, String message)
            throws Exception {
        language = locale;
        config.node("movement-attacks").set(true);
        startEncounter();
        player.setOp(true);
        player.setHealth(1);
        var deaths = new java.util.ArrayList<net.kyori.adventure.text.Component>();
        deathOutcome = source -> {
            var event = new org.bukkit.event.entity.PlayerDeathEvent(player, source, List.of(), 0,
                    net.kyori.adventure.text.Component.text("vanilla"), false);
            server.getPluginManager().callEvent(event);
            deaths.add(event.deathMessage());
        };
        debug("trigger " + attack, player);
        if (attack.equals("fireball-rain")) {
            scheduler.advance(Duration.ofSeconds(1));
            var ball = end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class).iterator().next();
            ball.teleport(player.getLocation().add(0, 1.5, 0));
            server.getPluginManager().callEvent(new org.bukkit.event.entity.ProjectileHitEvent(ball, player));
        } else {
            scheduler.advance(Duration.ofSeconds(8));
        }
        assertThat(deaths).isNotEmpty().allSatisfy(component -> assertThat(
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component))
                        .isEqualTo(player.getName() + " " + message));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
            "en|dragon| challenged the Ender Dragon and lost.",
            "de|dragon| forderte den Enderdrachen heraus und verlor.",
            "en|minion| fell to a servant of the Ender Dragon.",
            "de|minion| fiel einem Diener des Enderdrachen zum Opfer.",
            "en|dragon-part| challenged the Ender Dragon and lost.",
            "en|dragon-cloud| challenged the Ender Dragon and lost.",
            "en|minion-arrow| fell to a servant of the Ender Dragon.",
            "en|minion-tnt| fell to a servant of the Ender Dragon."})
    void nativeEncounterDeathsUseLocalizedFallbacks(String locale, String cause, String message) throws Exception {
        language = locale;
        startEncounter();
        org.bukkit.entity.Entity attacker = cause.startsWith("dragon") ? dragon : defenders().getFirst();
        switch (cause) {
            case "dragon-part" -> {
                var part = mock(org.bukkit.entity.ComplexEntityPart.class);
                org.mockito.Mockito.when(part.getParent()).thenReturn(dragon);
                attacker = part;
            }
            case "dragon-cloud" -> {
                var cloud = mock(org.bukkit.entity.AreaEffectCloud.class);
                org.mockito.Mockito.when(cloud.getSource()).thenReturn(dragon);
                attacker = cloud;
            }
            case "minion-arrow" -> {
                var arrow = mock(org.bukkit.entity.Arrow.class);
                org.mockito.Mockito.when(arrow.getShooter()).thenReturn(defenders().getFirst());
                attacker = arrow;
            }
            case "minion-tnt" -> {
                var tnt = mock(org.bukkit.entity.TNTPrimed.class);
                org.mockito.Mockito.when(tnt.getSource()).thenReturn(defenders().getFirst());
                attacker = tnt;
            }
            default -> {
            }
        }
        var source = DamageSource.builder(DamageType.MOB_ATTACK).withDirectEntity(attacker).build();
        var event = new org.bukkit.event.entity.PlayerDeathEvent(player, source, List.of(), 0,
                net.kyori.adventure.text.Component.text("vanilla"), false);
        server.getPluginManager().callEvent(event);
        assertThat(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.deathMessage())).isEqualTo(player.getName() + " " + message);
    }

    @Test
    void unrelatedAndSuppressedDeathsStayUnchangedAndAttackAttributionDoesNotLinger() throws Exception {
        startEncounter();
        player.setOp(true);
        debug("trigger bombardment", player);
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(player.getHealth()).isLessThan(20).isGreaterThan(0);
        var otherDragon = end.spawn(new Location(end, 30, 80, 0), EnderDragon.class);
        for (var source : List.of(DamageSource.builder(DamageType.FALL).build(),
                DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(otherDragon).build())) {
            var event = new org.bukkit.event.entity.PlayerDeathEvent(player, source, List.of(), 0,
                    net.kyori.adventure.text.Component.text("vanilla"), false);
            server.getPluginManager().callEvent(event);
            assertThat(event.deathMessage()).isEqualTo(net.kyori.adventure.text.Component.text("vanilla"));
        }
        var source = DamageSource.builder(DamageType.DRAGON_BREATH).withDirectEntity(dragon)
                .withCausingEntity(dragon).build();
        var later = new org.bukkit.event.entity.PlayerDeathEvent(player, source, List.of(), 0,
                net.kyori.adventure.text.Component.text("vanilla"), false);
        server.getPluginManager().callEvent(later);
        assertThat(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(later.deathMessage()))
                        .isEqualTo(player.getName() + " challenged the Ender Dragon and lost.");
        for (boolean cancelled : List.of(false, true)) {
            var suppressed = new org.bukkit.event.entity.PlayerDeathEvent(player, source, List.of(), 0,
                    cancelled ? net.kyori.adventure.text.Component.text("vanilla") : null, false);
            suppressed.setCancelled(cancelled);
            server.getPluginManager().callEvent(suppressed);
            assertThat(suppressed.deathMessage()).isEqualTo(
                    cancelled ? net.kyori.adventure.text.Component.text("vanilla") : null);
        }
    }

    @Test
    void fireballRainLocksTheWarnedGroundBeforeLaunchingAndDoesNotFollowThePlayer() throws Exception {
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        Location locked = player.getLocation();
        assertThat(debug("trigger fireball-rain " + player.getName(), player)).isEqualTo(1);
        assertThat(end.getEntitiesByClass(TextDisplay.class))
                .anyMatch(display -> display.getLocation().getX() == locked.getX()
                        && display.getLocation().getZ() == locked.getZ());
        assertThat(end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class)).isEmpty();
        player.teleport(locked.clone().add(12, 0, 0));
        scheduler.advance(Duration.ofMillis(999));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class)).isEmpty();
        scheduler.advance(Duration.ofMillis(1));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class)).singleElement().satisfies(ball -> {
            assertThat(ball.getLocation()).isEqualTo(locked.clone().add(0, 20, 0));
            assertThat(ball.getVelocity().getX()).isZero();
            assertThat(ball.getVelocity().getZ()).isZero();
            assertThat(ball.getVelocity().getY()).isEqualTo(-0.5);
            assertThat(ball.getShooter()).isEqualTo(dragon);
        });
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(player.getHealth()).isEqualTo(20);
        debug("cancel", player);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class)).isEmpty();
    }

    @Test
    void rainWarningMatchesInclusiveExplosionAndDamagePoolBoundary() throws Exception {
        for (int x = 4; x <= 17; x++) {
            for (int z = -6; z <= 6; z++) {
                end.getBlockAt(x, 62, z).setType(Material.END_STONE);
            }
        }
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        Location locked = player.getLocation();
        var boundary = server.addPlayer("PoolBoundary");
        boundary.teleport(locked.clone().add(0, 0, 5));
        var outside = server.addPlayer("OutsidePool");
        outside.teleport(locked.clone().add(0, 0, 5.01));
        var warningPoints = new java.util.ArrayList<Location>();
        try (var particles = org.mockito.Mockito.mockStatic(CrownParticles.class, call -> {
            if (call.getArgument(1) == org.bukkit.Particle.FLAME) {
                warningPoints.add(((Location) call.getArgument(2)).clone());
            }
            return null;
        })) {
            debug("trigger fireball-rain " + player.getName(), player);
            scheduler.advance(Duration.ofSeconds(1));
            assertThat(warningPoints).isNotEmpty().allSatisfy(
                    point -> assertThat(Math.hypot(point.getX() - locked.getX(), point.getZ() - locked.getZ()))
                            .isCloseTo(5, org.assertj.core.data.Offset.offset(0.000001)));
            var ball = end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class).iterator().next();
            ball.teleport(locked.clone());
            server.getPluginManager().callEvent(new org.bukkit.event.entity.ProjectileHitEvent(ball,
                    end.getBlockAt(locked.getBlockX(), 63, locked.getBlockZ())));
            assertThat(boundary.getHealth()).isEqualTo(16);
            assertThat(outside.getHealth()).isEqualTo(20);
            scheduler.advance(Duration.ZERO);
            assertThat(boundary.getHealth()).isEqualTo(13);
            assertThat(outside.getHealth()).isEqualTo(20);
        }
    }

    @Test
    void rainCollisionResolvesOnceMakesPermanentCraterAndReleasesMainBeforeItsPool() throws Exception {
        config.node("pool-damage").set(0);
        for (int x = 4; x <= 17; x++) {
            for (int z = -6; z <= 6; z++) {
                end.getBlockAt(x, 62, z).setType(Material.END_STONE);
            }
        }
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        debug("trigger fireball-rain " + player.getName(), player);
        scheduler.advance(Duration.ofSeconds(1));
        var ball = end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class).iterator().next();
        ball.teleport(player.getLocation().add(0, 1.5, 0));
        var collision = new org.bukkit.event.entity.ProjectileHitEvent(ball, player);
        server.getPluginManager().callEvent(collision);
        assertThat(collision.isCancelled()).isTrue();
        assertThat(player.getHealth()).isEqualTo(16);
        assertThat(referenceHits).isEqualTo(1);
        assertThat(ball.isValid()).isFalse();
        assertThat(end.getBlockAt(10, 63, 0).getType()).isEqualTo(Material.AIR);
        assertThat(end.getBlockAt(10, 62, 0).getType()).isEqualTo(Material.END_STONE);
        assertThat(end.getBlockAt(15, 63, 0).getType()).isEqualTo(Material.AIR);
        assertThat(end.getBlockAt(15, 62, 0).getType()).isEqualTo(Material.END_STONE);
        assertThat(end.getBlockAt(16, 63, 0).getType()).isEqualTo(Material.END_STONE);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.FallingBlock.class)).hasSize(81);
        debug("status", player);
        assertThat(messages(player)).anyMatch(message -> message.contains("main=idle") && message.contains("areas=1"));
        server.getPluginManager().callEvent(new org.bukkit.event.entity.ProjectileHitEvent(ball, player));
        var prime = new org.bukkit.event.entity.ExplosionPrimeEvent(ball);
        server.getPluginManager().callEvent(prime);
        assertThat(prime.isCancelled()).isTrue();
        assertThat(referenceHits).isEqualTo(1);
        scheduler.advance(Duration.ofSeconds(6));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        debug("cancel", player);
        assertThat(end.getBlockAt(10, 63, 0).getType()).isEqualTo(Material.AIR);
    }

    @Test
    void unnamedRainTargetsAllCombatPlayersNotThePreyPercentageAndWaitsForLastResolution() throws Exception {
        config.node("prey-percentage").set(35);
        var adventure = server.addPlayer("Adventure");
        adventure.teleport(player.getLocation().add(8, 0, 0));
        adventure.setGameMode(org.bukkit.GameMode.ADVENTURE);
        var third = server.addPlayer("CombatThree");
        third.teleport(player.getLocation().add(16, 0, 0));
        var observer = server.addPlayer("Observer");
        observer.teleport(player.getLocation());
        observer.setGameMode(org.bukkit.GameMode.SPECTATOR);
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        debug("trigger fireball-rain", player);
        scheduler.advance(Duration.ofSeconds(1));
        var balls = List.copyOf(end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class));
        assertThat(balls).hasSize(3); // Four connected players request two Prey marks, but three Rain shots.
        balls.getFirst().remove();
        scheduler.advance(Duration.ofMillis(50));
        debug("status", player);
        assertThat(messages(player))
                .anyMatch(message -> message.contains("attack=RAIN") && message.contains("main=executing"));
        balls.getLast().remove();
        balls.get(1).remove();
        scheduler.advance(Duration.ofMillis(50));
        debug("status", player);
        assertThat(messages(player)).anyMatch(message -> message.contains("main=idle"));
        scheduler.advance(Duration.ofSeconds(6));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"rain-spawn-height,15", "rain-spawn-height,25",
            "rain-spawn-height,NaN", "rain-descent-seconds,1", "rain-descent-seconds,3",
            "rain-descent-seconds,Infinity", "rain-damage,-1", "rain-damage,101"})
    void rejectsInvalidRainConfiguration(String key, String value) throws Exception {
        config.node(key).set(value);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ShatteredCrownConfig.load(config))
                .isInstanceOf(Exception.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0,0,1", "0,4,1", "3,4,0.875", "-3,-4,0.875", "0,8,0.5",
            "6,8,0"})
    void smashFalloffUsesRadialHorizontalDistanceRatherThanOneAxis(double x, double z, double scale) {
        var center = new Location(end, 10.5, 64, 0.5);
        var area = new HuntArea(center, 8);
        assertThat(area.smashDamageScale(center.clone().add(x, 0, z))).isEqualTo(scale);
        assertThat(area.smashDamageScale(center.clone().add(x, 4, z))).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"cancel,false", "cancel,true", "pause,false", "pause,true",
            "unload,false", "unload,true", "disable,false", "disable,true", "victory,false", "victory,true",
            "reset,false", "reset,true", "wave,false", "wave,true"})
    void rainTerminationClosesWarningsProjectilesPoolsAndCallbacksWithoutRestoringGround(String termination,
            boolean impact) throws Exception {
        config.node("pool-damage").set(0);
        for (int x = 4; x <= 17; x++) {
            for (int z = -6; z <= 6; z++) {
                end.getBlockAt(x, 62, z).setType(Material.END_STONE);
            }
        }
        if (termination.equals("victory")) {
            hunt();
            clearFinalGate();
        } else {
            startEncounter();
        }
        player.setOp(true);
        debug("trigger fireball-rain " + player.getName(), player);
        scheduler.advance(Duration.ofSeconds(1));
        var ball = end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class).iterator().next();
        if (impact) {
            ball.teleport(player.getLocation().add(0, 1.5, 0));
            server.getPluginManager().callEvent(new org.bukkit.event.entity.ProjectileHitEvent(ball, player));
        }
        var unrelated = end.spawn(new Location(end, 35, 80, 35), org.bukkit.entity.LargeFireball.class);
        switch (termination) {
            case "cancel", "pause" -> debug(termination, player);
            case "reset" -> {
                player.teleport(new Location(end, 100, 50, 0));
                scheduler.advance(Duration.ofSeconds(60));
            }
            case "wave" -> debug("wave 2", player);
            case "unload" -> server.getPluginManager().callEvent(new org.bukkit.event.world.WorldUnloadEvent(end));
            case "disable" -> module.disable();
            case "victory" -> server.getPluginManager().callEvent(
                    new EntityDeathEvent(dragon, mock(DamageSource.class), List.of()));
            default -> throw new AssertionError(termination);
        }
        server.getPluginManager().callEvent(new org.bukkit.event.entity.ProjectileHitEvent(ball, player));
        scheduler.advance(Duration.ofSeconds(20));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class)).containsExactly(unrelated);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(end.getEntitiesByClass(org.bukkit.entity.FallingBlock.class)).isEmpty();
        assertThat(end.getBlockAt(10, 63, 0).getType()).isEqualTo(impact ? Material.AIR : Material.END_STONE);
        assertThat(referenceHits).isEqualTo(impact ? 1 : 0);
    }

    @Test
    void rainReferenceHitCanBeCancelledAndNativeDamageAndDeflectionRemainSuppressed() throws Exception {
        startEncounter();
        player.setOp(true);
        debug("trigger fireball-rain " + player.getName(), player);
        scheduler.advance(Duration.ofSeconds(1));
        var ball = end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class).iterator().next();
        var attack = new EntityDamageByEntityEvent(player, ball, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(player).withCausingEntity(player)
                        .build(),
                1);
        server.getPluginManager().callEvent(attack);
        assertThat(attack.isCancelled()).isTrue();
        var nativeHit = new EntityDamageByEntityEvent(ball, player, EntityDamageEvent.DamageCause.PROJECTILE,
                DamageSource.builder(DamageType.FIREBALL).withDirectEntity(ball).withCausingEntity(dragon).build(), 6);
        server.getPluginManager().callEvent(nativeHit);
        assertThat(nativeHit.isCancelled()).isTrue();
        var listener = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void cancel(EntityDamageEvent event) {
                if (event.getEntity() == player && event.getDamageSource().getCausingEntity() == dragon) {
                    event.setCancelled(true);
                }
            }
        };
        server.getPluginManager().registerEvents(listener, plugin);
        ball.teleport(player.getLocation().add(0, 1.5, 0));
        server.getPluginManager().callEvent(new org.bukkit.event.entity.ProjectileHitEvent(ball, player));
        assertThat(referenceHits).isEqualTo(1);
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(end.getBlockAt(10, 63, 0).isEmpty()).isTrue();
    }

    @Test
    void rainChangedTuningIsNextAttemptOnlyAndStaleEntitiesLoadRecoveryIsScoped() throws Exception {
        startEncounter();
        var next = config.copy();
        next.node("rain-spawn-height").set(22);
        next.node("rain-descent-seconds").set(2.2);
        next.node("rain-damage").set(2);
        module.onConfigReload(next);
        player.setOp(true);
        debug("trigger fireball-rain " + player.getName(), player);
        scheduler.advance(Duration.ofSeconds(1));
        var original = end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class).iterator().next();
        assertThat(original.getY()).isEqualTo(84);
        debug("cancel", player);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(60));
        player.teleport(new Location(end, 10.5, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(1));
        debug("trigger fireball-rain " + player.getName(), player);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class)).singleElement().satisfies(ball -> {
            assertThat(ball.getY()).isEqualTo(86);
            assertThat(ball.getVelocity().getY()).isEqualTo(-0.5);
            ball.teleport(player.getLocation().add(0, 1.5, 0));
            server.getPluginManager().callEvent(new org.bukkit.event.entity.ProjectileHitEvent(ball, player));
        });
        assertThat(player.getHealth()).isEqualTo(18);
        debug("cancel", player);
        var stale = end.spawn(new Location(end, 10, 80, 0), org.bukkit.entity.LargeFireball.class);
        stale.getPersistentDataContainer().set(EncounterRecovery.ARTIFACT,
                org.bukkit.persistence.PersistentDataType.STRING, "rain-projectile");
        var unrelated = end.spawn(new Location(end, 35, 80, 35), org.bukkit.entity.LargeFireball.class);
        var loaded = new org.bukkit.event.world.EntitiesLoadEvent(end.getChunkAt(0, 0), List.of(stale, unrelated));
        server.getPluginManager().callEvent(loaded);
        server.getPluginManager().callEvent(loaded);
        assertThat(stale.isValid()).isFalse();
        assertThat(unrelated.isValid()).isTrue();
    }

    @Test
    void rainTargetsHighCombatPlayersAndDestroysArrivalGroundWithoutHurtingProtectedPlayers() throws Exception {
        config.node("arrival-x").set(10.5);
        config.node("arrival-y").set(64);
        config.node("arrival-radius").set(3);
        config.node("pool-damage").set(0);
        end.getBlockAt(10, 62, 0).setType(Material.END_STONE);
        player.teleport(new Location(end, 10.5, 100, 0.5)); // Eligible above the arrival cylinder's height.
        startEncounter();
        player.setOp(true);
        debug("trigger fireball-rain " + player.getName(), player);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class)).singleElement().satisfies(ball -> {
            assertThat(ball.getY()).isEqualTo(84); // Lock actual ground, not the airborne player's Y.
            player.teleport(new Location(end, 10.5, 64, 0.5));
            ball.teleport(player.getLocation().add(0, 1.5, 0));
            server.getPluginManager().callEvent(new org.bukkit.event.entity.ProjectileHitEvent(ball, player));
        });
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(referenceHits).isZero();
        assertThat(end.getBlockAt(10, 63, 0).isEmpty()).isTrue();
        debug("status", player);
        assertThat(messages(player)).anyMatch(message -> message.contains("areas=1") && message.contains("main=idle"));
    }

    @Test
    void rainNativeBlockHitJustInsideTheTopFaceStillCraterAndDepositsOnTheNewSurface() throws Exception {
        config.node("pool-damage").set(0);
        for (int x = 4; x <= 17; x++) {
            for (int z = -6; z <= 6; z++) {
                end.getBlockAt(x, 62, z).setType(Material.END_STONE);
            }
        }
        startEncounter();
        player.setOp(true);
        debug("trigger fireball-rain " + player.getName(), player);
        scheduler.advance(Duration.ofSeconds(1));
        var ball = end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class).iterator().next();
        ball.teleport(new Location(end, 10.5, 63.9995, 0.5)); // Actual Paper ray collision epsilon observed natively.
        player.teleport(new Location(end, 30.5, 64, 0.5));
        server.getPluginManager()
                .callEvent(new org.bukkit.event.entity.ProjectileHitEvent(ball, end.getBlockAt(10, 63, 0)));
        assertThat(end.getBlockAt(10, 63, 0).isEmpty()).isTrue();
        assertThat(end.getBlockAt(10, 62, 0).getType()).isEqualTo(Material.END_STONE);
        debug("status", player);
        assertThat(messages(player)).anyMatch(message -> message.contains("areas=1") && message.contains("main=idle"));
        assertThat(player.getHealth()).isEqualTo(20);
    }

    @Test
    void aPlayerProjectileCannotDeflectAnOwnedRainFireballBeforeNativeDamage() throws Exception {
        startEncounter();
        player.setOp(true);
        debug("trigger fireball-rain " + player.getName(), player);
        scheduler.advance(Duration.ofSeconds(1));
        var ball = end.getEntitiesByClass(org.bukkit.entity.LargeFireball.class).iterator().next();
        var arrow = end.spawn(player.getLocation(), org.bukkit.entity.Arrow.class);
        arrow.setShooter(player);
        var collision = new org.bukkit.event.entity.ProjectileHitEvent(arrow, ball);
        server.getPluginManager().callEvent(collision);
        assertThat(collision.isCancelled()).isTrue();
        assertThat(ball.isValid()).isTrue();
        assertThat(ball.getShooter()).isEqualTo(dragon);
        assertThat(ball.getVelocity().getY()).isEqualTo(-0.5);
    }

    @Test
    void sharedRainPoolKeepsDamagingDuringAnotherNativeMovementMainAction() throws Exception {
        config.node("movement-attacks").set(true);
        startEncounter();
        player.setOp(true);
        debug("trigger dive " + player.getName(), player);
        var encounter = org.bukkit.event.HandlerList.getRegisteredListeners(plugin).stream()
                .map(org.bukkit.plugin.RegisteredListener::getListener).filter(EncounterController.class::isInstance)
                .map(EncounterController.class::cast).findFirst().orElseThrow();
        assertThat(encounter.depositBreathPool(player.getLocation(), 5, 6)).isTrue();
        scheduler.advance(Duration.ZERO);
        assertThat(player.getHealth()).isEqualTo(17);
        debug("status", player);
        assertThat(messages(player))
                .anyMatch(message -> message.contains("attack=DIVE") && message.contains("areas=1"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(EncounterController.DebugAttack.class)
    void debugTriggersActualAttacksDuringOpeningAndCancelKeepsRotationPaused(EncounterController.DebugAttack attack)
            throws Exception {
        config.node("movement-attacks").set(true);
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        assertThat(debug("trigger " + (attack == EncounterController.DebugAttack.RAIN ? "fireball-rain"
                : attack.name().toLowerCase(java.util.Locale.ROOT)) + " " + player.getName(), player)).isEqualTo(1);
        assertThat(messages(player)).singleElement().asString().contains("Started " + attack);
        assertThat(hit(dragon).isCancelled()).isTrue(); // Debugging attacks does not clear the shield.
        switch (attack) {
            case DIVE -> {
                scheduler.advance(Duration.ofSeconds(6));
                assertThat(player.getHealth()).isEqualTo(12);
            }
            case SMASH -> {
                end.getBlockAt(10, 62, 0).setType(Material.END_STONE); // Surviving ground below the new crater.
                scheduler.advance(Duration.ofSeconds(5));
                assertThat(player.getHealth()).isEqualTo(8);
            }
            case PREY -> {
                scheduler.advance(Duration.ofSeconds(4));
                assertThat(player.getHealth()).isEqualTo(17);
            }
            case BOMBARDMENT -> {
                scheduler.advance(Duration.ofSeconds(3));
                assertThat(player.getHealth()).isEqualTo(14);
                assertThat(referenceHits).isEqualTo(1);
            }
            case LANDING -> assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.FLY_TO_PORTAL);
        }
        assertThat(messages(player)).isEmpty();
        assertThat(actionBars(player)).isEmpty();
        debug("cancel", player);
        assertThat(messages(player)).singleElement().asString().contains("mode=manual", "attack=none");
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        scheduler.advance(Duration.ofSeconds(12));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        debug("status", player);
        assertThat(messages(player)).singleElement().asString().contains("mode=manual", "attack=none");
        debug("resume", player);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isNotEmpty();
    }

    @Test
    void completedPreyKeepsAnOwnedDamagingPoolUntilOperatorCancellation() throws Exception {
        config.node("movement-attacks").set(true);
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        debug("trigger prey", player);
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("attack=PREY") && text.contains("hazards=0"));
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        assertThat(player.getHealth()).isEqualTo(17);
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("attack=none") && text.contains("hazards=1"));
        debug("cancel", player);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofSeconds(12));
        assertThat(player.getHealth()).isEqualTo(17);
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("attack=none") && text.contains("hazards=0"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"1,false", "3,false", "15,false", "3,true"})
    void bombardmentFlamesDescendWithoutCountdownAndStopAtImpactOrCancellation(int warning, boolean cancel)
            throws Exception {
        config.node("bombardment-warning-seconds").set(warning);
        config.node("bombardment-cadence-seconds").set(warning + 2);
        startEncounter();
        player.setOp(true);
        Location impact = player.getLocation();
        var falling = new java.util.ArrayList<Double>();
        var accents = new java.util.ArrayList<Double>();
        var ring = new java.util.ArrayList<Location>();
        int[] bursts = {0};
        try (var particles = org.mockito.Mockito.mockStatic(CrownParticles.class, call -> {
            if (call.getArgument(0) == player && call.getArgument(1) == org.bukkit.Particle.FLAME) {
                Location location = call.getArgument(2);
                if (location.getX() == impact.getX() && location.getZ() == impact.getZ()) {
                    falling.add(location.getY());
                    assertThat(call.<Integer>getArgument(3)).isEqualTo(8);
                } else {
                    ring.add(location.clone());
                }
            }
            if (call.getArgument(0) == player && call.getArgument(1) == org.bukkit.Particle.DUST) {
                Location location = call.getArgument(2);
                assertThat(location.getX()).isEqualTo(impact.getX());
                assertThat(location.getZ()).isEqualTo(impact.getZ());
                assertThat(call.<Integer>getArgument(3)).isEqualTo(2);
                accents.add(location.getY());
            }
            if (call.getArgument(0) == player && call.getArgument(1) == org.bukkit.Particle.EXPLOSION) {
                bursts[0]++;
                assertThat(call.<Integer>getArgument(3)).isEqualTo(4);
            }
            return null;
        })) {
            debug("trigger bombardment", player);
            assertThat(falling).containsExactly(impact.getY() + 20);
            // Each accent call carries count 2 (two purple particles per eight flames).
            assertThat(accents).containsExactlyElementsOf(falling);
            assertThat(ring).hasSize(32).allSatisfy(location -> assertThat(location.getY())
                    .isCloseTo(impact.getY() + 0.15, org.assertj.core.data.Offset.offset(0.000001)));
            var marker = end.getEntitiesByClass(TextDisplay.class).iterator().next();
            assertThat(marker.text()).isEqualTo(net.kyori.adventure.text.Component.empty());
            scheduler.advance(Duration.ofMillis(warning * 500L));
            assertThat(player.getHealth()).isEqualTo(20);
            assertThat(bursts[0]).isZero();
            assertThat(falling.getLast()).isCloseTo(impact.getY() + 10.25,
                    org.assertj.core.data.Offset.offset(0.000001));
            assertThat(marker.text()).isEqualTo(net.kyori.adventure.text.Component.empty());
            for (int i = 1; i < falling.size(); i++) {
                assertThat(falling.get(i)).isLessThan(falling.get(i - 1));
            }
            if (cancel) {
                debug("cancel", player);
            } else {
                scheduler.advance(Duration.ofMillis(warning * 500L - 1));
                assertThat(referenceHits).isZero();
                scheduler.advance(Duration.ofMillis(1));
                assertThat(falling.getLast()).isCloseTo(impact.getY() + 0.5,
                        org.assertj.core.data.Offset.offset(0.000001));
            }
            assertThat(referenceHits).isEqualTo(cancel ? 0 : 1);
            assertThat(bursts[0]).isEqualTo(cancel ? 0 : 1);
            assertThat(accents).containsExactlyElementsOf(falling);
            assertThat(player.getHealth()).isEqualTo(cancel ? 20 : 14);
            assertThat(marker.isValid()).isFalse();
            int emitted = falling.size() + ring.size() + accents.size() + bursts[0];
            scheduler.advance(Duration.ofSeconds(warning + 2));
            assertThat(falling.size() + ring.size() + accents.size() + bursts[0]).isEqualTo(emitted);
            assertThat(end.getEntitiesByClass(org.bukkit.entity.Fireball.class)).isEmpty();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"cancel", "wave 2"})
    void bombardmentStopsBetweenRecipientsWhenDamageListenerClosesItsOwner(String action) throws Exception {
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        var teammate = server.addPlayer();
        teammate.teleport(player.getLocation());
        int[] hits = {0};
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void replace(EntityDamageEvent event) throws Exception {
                if (event.getEntity() instanceof org.bukkit.entity.Player
                        && event.getDamageSource().getCausingEntity() == dragon && ++hits[0] == 1) {
                    debug(action, player);
                }
            }
        }, plugin);
        debug("trigger bombardment", player);
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(hits[0]).isEqualTo(1);
        assertThat(List.of(player.getHealth(), teammate.getHealth())).containsExactlyInAnyOrder(14.0, 20.0);
        assertThat(dragon.isValid()).isTrue();
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("main=idle") && text.contains("hazards=0"));
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(hits[0]).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"English,surface outside combat area",
            "Deutsch,Oberfläche außerhalb des Kampfbereichs"})
    void geometryRejectionUsesTheRequestersLanguage(String name, String reason) throws Exception {
        startEncounter();
        var requester = server.addPlayer(name);
        requester.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        for (int x = 180; x <= 200; x++) {
            for (int z = -10; z <= 10; z++) {
                end.getBlockAt(x, 63, z).setType(Material.END_STONE);
            }
        }
        player.teleport(new Location(end, 190, 64, 0));
        debug("trigger smash " + player.getName(), requester);
        assertThat(messages(requester)).singleElement().asString().contains(reason);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void failedPreyRefreshClearsMainOwnershipAndAllowsAnotherProductionTrigger() throws Exception {
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        debug("trigger prey", player);
        try (var particles = org.mockito.Mockito.mockStatic(CrownParticles.class)) {
            particles.when(() -> CrownParticles.spawn(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyDouble(),
                    org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                    org.mockito.ArgumentMatchers.anyDouble())).thenThrow(new IllegalStateException("render fault"));
            scheduler.advance(Duration.ofMillis(250));
        }
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("attack=none") && text.contains("hazards=0"));
        debug("trigger bombardment", player);
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(14);
        assertThat(referenceHits).isEqualTo(1);
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("main=idle"));
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(player.getHealth()).isEqualTo(14);
        assertThat(referenceHits).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(EncounterController.DebugAttack.class)
    void everyCurrentAttackCompletesItsMainActionWithoutRequiringOperatorReset(EncounterController.DebugAttack attack)
            throws Exception {
        config.node("movement-attacks").set(true);
        config.node("lane-damage").set(0);
        config.node("sweep-damage").set(0);
        config.node("pool-damage").set(0);
        config.node("bombardment-damage").set(0);
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        debug("trigger " + (attack == EncounterController.DebugAttack.RAIN ? "fireball-rain"
                : attack.name().toLowerCase(java.util.Locale.ROOT)), player);
        debug("status", player);
        assertThat(messages(player))
                .anyMatch(text -> text.contains("main=executing") && text.contains("attack=" + attack));
        scheduler.advance(Duration.ofSeconds(21));
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("main=idle") && text.contains("attack=none"));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void cancellationInsidePublicWarningSpawnCannotOrphanTheNewEntity() throws Exception {
        hunt();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        var listener = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void spawned(org.bukkit.event.entity.EntitySpawnEvent event) throws Exception {
                if (event.getEntity() instanceof TextDisplay) {
                    debug("cancel", player);
                }
            }
        };
        server.getPluginManager().registerEvents(listener, plugin);
        debug("trigger prey", player);
        org.bukkit.event.HandlerList.unregisterAll(listener);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(player.getHealth()).isEqualTo(20);
        debug("trigger prey", player);
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(player.getHealth()).isEqualTo(17);
    }

    @Test
    void aSchedulerCancellationFailureDoesNotLeaveAnAttackOrPreventTheNextTrigger() throws Exception {
        scheduler = org.mockito.Mockito.spy(scheduler);
        org.mockito.Mockito.doAnswer(call -> {
            var task = (de.kreiscraft.core.scheduler.Scheduler.Task) call.callRealMethod();
            if (!Duration.ofMillis(250).equals(call.getArgument(1))) {
                return task;
            }
            return new de.kreiscraft.core.scheduler.Scheduler.Task() {
                @Override
                public void cancel() {
                    task.cancel();
                    throw new IllegalStateException("scheduler cancellation fault");
                }

                @Override
                public boolean isCancelled() {
                    return task.isCancelled();
                }
            };
        }).when(scheduler).runRepeating(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        debug("trigger bombardment", player);
        debug("cancel", player);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(referenceHits).isZero();
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("attack=none") && text.contains("main=idle"));
        debug("trigger prey", player);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        debug("cancel", player);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void diveTimingOverridesAreSnapshottedAndValidatedAtModuleBoundary() throws Exception {
        config.node("movement-attacks").set(true);
        config.node("lane-warning-seconds").set(4);
        config.node("dive-pass-seconds").set(3);
        config.node("dive-climb-seconds").set(2);
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        debug("trigger dive", player);
        config.node("dive-pass-seconds").set(1);
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(dragon.getY()).isEqualTo(78);
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(dragon.getY()).isEqualTo(66);
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        module.disable();
        config.node("dive-climb-seconds").set(0.1);
        org.assertj.core.api.Assertions.assertThatThrownBy(this::startEncounter)
                .hasMessageContaining("dive-climb-seconds");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"world", "mode", "terrain", "phase", "cancel", "disable"})
    void diveInvalidityReleasesMainAndNativeControlWithQueuedCallbacksInert(String change) throws Exception {
        config.node("movement-attacks").set(true);
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        debug("trigger dive", player);
        scheduler.advance(Duration.ofSeconds(1));
        switch (change) {
            case "world" -> player.teleport(new Location(server.addSimpleWorld("elsewhere"), 0, 64, 0));
            case "mode" -> player.setGameMode(org.bukkit.GameMode.CREATIVE);
            case "terrain" -> end.getBlockAt(11, 63, 0).setType(Material.AIR);
            case "phase" -> dragon.setPhase(EnderDragon.Phase.CIRCLING);
            case "cancel" -> debug("cancel", player);
            case "disable" -> module.disable();
            default -> throw new AssertionError(change);
        }
        scheduler.advance(Duration.ofMillis(250));
        Location stopped = dragon.getLocation();
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(dragon.getLocation()).isEqualTo(stopped);
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(referenceHits).isZero();
        if (!change.equals("disable")) {
            debug("status", player);
            assertThat(messages(player)).anyMatch(text -> text.contains("main=idle") && text.contains("flight=false"));
        }
    }

    @Test
    void diveAcrossExpandedArenaStagesLongApproachWithinNativeStepBounds() throws Exception {
        config.node("movement-attacks").set(true);
        for (int x = 155; x <= 191; x++) {
            for (int z = -18; z <= 18; z++) {
                end.getBlockAt(x, 63, z).setType(Material.END_STONE);
            }
        }
        player.teleport(new Location(end, 178.5, 64, 0.5));
        dragon.teleport(new Location(end, -180, 80, 0));
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        debug("trigger dive", player);
        scheduler.advance(Duration.ofSeconds(8));
        assertThat(referenceHits).isEqualTo(1);
        assertThat(player.getHealth()).isEqualTo(12);
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        assertThat(dragon.getX()).isGreaterThan(178);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void diveHidesBothCountdownsWithoutChangingWarningPassOrOtherEffects() throws Exception {
        config.node("movement-attacks").set(true);
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        try (var particles = org.mockito.Mockito.mockStatic(CrownParticles.class)) {
            debug("trigger dive", player);
            messages(player); // The explicit operator reply is not an automatic attack announcement.
            scheduler.advance(Duration.ofMillis(250));
            var display = end.getEntitiesByClass(TextDisplay.class).iterator().next();
            assertThat(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(display.text())).isEmpty();
            assertThat(player.getHealth()).isEqualTo(20);
            scheduler.advance(Duration.ofSeconds(2));
            assertThat(display.isValid()).isTrue();
            assertThat(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(display.text())).isEmpty();
            scheduler.advance(Duration.ofMillis(2250));
            particles.verifyNoInteractions();
            assertThat(messages(player)).isEmpty();
            assertThat(actionBars(player)).isEmpty();
            assertThat(referenceHits).isEqualTo(1);
        }
    }

    @Test
    void aggressiveDiveTravelsFortyBlocksWithEarlyGroundHitThirtyTickPassAndFullWarning() throws Exception {
        config.node("movement-attacks").set(true);
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        debug("trigger dive", player);
        scheduler.advance(Duration.ofMillis(1950));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(dragon.getY()).isGreaterThan(77);
        scheduler.advance(Duration.ofMillis(50));
        assertThat(dragon.getY()).isEqualTo(78);
        Location passStart = dragon.getLocation();
        assertThat(Math.hypot(passStart.getX() - player.getX(), passStart.getZ() - player.getZ()))
                .isEqualTo(20);
        var earlyTarget = server.addPlayer("EarlyDiveTarget");
        var direction = player.getLocation().toVector().subtract(passStart.toVector()).setY(0).normalize();
        Location earlyPosition = passStart.clone().add(direction.multiply(4));
        earlyPosition.setY(player.getY());
        earlyTarget.teleport(earlyPosition);
        scheduler.advance(Duration.ofMillis(100));
        assertThat(earlyTarget.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofMillis(50));
        Location groundPassStart = dragon.getLocation();
        assertThat(groundPassStart.getY()).isLessThanOrEqualTo(69);
        assertThat(earlyTarget.getHealth()).isEqualTo(12);
        assertThat(referenceHits).isEqualTo(1);
        scheduler.advance(Duration.ofMillis(100));
        assertThat(dragon.getY()).isEqualTo(66);
        scheduler.advance(Duration.ofMillis(500));
        assertThat(dragon.getY()).isEqualTo(66);
        scheduler.advance(Duration.ofMillis(750));
        assertThat(dragon.getY()).isEqualTo(66);
        assertThat(Math.hypot(dragon.getX() - passStart.getX(), dragon.getZ() - passStart.getZ()))
                .isEqualTo(40);
        assertThat(Math.hypot(dragon.getX() - groundPassStart.getX(), dragon.getZ() - groundPassStart.getZ()))
                .isEqualTo(36);
        assertThat(player.getHealth()).isEqualTo(12);
        assertThat(earlyTarget.getHealth()).isEqualTo(12);
        assertThat(referenceHits).isEqualTo(2);
        scheduler.advance(Duration.ofMillis(950));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CHARGE_PLAYER);
        scheduler.advance(Duration.ofMillis(50));
        assertThat(dragon.getY()).isEqualTo(82);
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("main=idle") && text.contains("attack=none"));
    }

    @Test
    void diveSignalsCompletionOnlyAfterReleasingNativeControl() throws Exception {
        config.node("movement-attacks").set(true);
        config.node("lane-damage").set(0);
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        int[] releases = {0};
        beforeNativeRelease = () -> {
            releases[0]++;
            try {
                debug("status", player);
            } catch (Exception error) {
                throw new AssertionError(error);
            }
            assertThat(messages(player))
                    .anyMatch(text -> text.contains("main=executing") && text.contains("attack=DIVE"));
        };
        debug("trigger dive", player);
        scheduler.advance(Duration.ofSeconds(9));
        assertThat(releases[0]).isEqualTo(1);
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("main=idle") && text.contains("attack=none"));
        debug("cancel", player);
        assertThat(releases[0]).isEqualTo(1);
    }

    @Test
    void debugCommandEnforcesPermissionAndUsesExplicitTarget() throws Exception {
        config.node("movement-attacks").set(true);
        hunt();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> debug("pause", player))
                .isInstanceOf(com.mojang.brigadier.exceptions.CommandSyntaxException.class);
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        var target = server.addPlayer("PreyTarget");
        target.teleport(new Location(end, 20, 64, 0));
        debug("trigger prey PreyTarget", player);
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(target.getHealth()).isEqualTo(17);
        assertThat(player.getHealth()).isEqualTo(20);
        debug("trigger dive MissingPlayer", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("MissingPlayer") && text.contains("not online"));
        debug("cancel", player);
        target.teleport(new Location(end, 100, 50, 0));
        debug("trigger smash PreyTarget", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("outside protected arrival"));
    }

    @Test
    void debugResumeReleasesManualHuntAndCommandsHandleConsoleAndDisabledModule() throws Exception {
        config.node("movement-attacks").set(true);
        hunt();
        var console = server.getConsoleSender();
        assertThat(debug("trigger dive", console)).isZero();
        assertThat(debug("trigger dive " + player.getName(), console)).isEqualTo(1);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CHARGE_PLAYER);
        debug("pause", console);
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        while (console.nextMessage() != null) {
            // Drain earlier explicit replies before checking resume.
        }
        debug("resume", console);
        assertThat(console.nextMessage()).contains("mode=automatic", "main=executing");
        module.disable();
        assertThat(debug("status", console)).isZero();
    }

    private EntityDamageEvent hit(org.bukkit.entity.Entity target) {
        var event = new EntityDamageByEntityEvent(player, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                mock(DamageSource.class), 10);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void trigger(String attack) throws Exception {
        player.setOp(true);
        debug("trigger " + attack, player);
        messages(player); // Geometry checks deliberately isolate an action from randomized scheduling.
    }

    @Test
    void explicitDebugRepliesAreLocalizedAndOnlyReachTheRequestingOperator() throws Exception {
        var german = server.addPlayer("Deutsch");
        german.teleport(player.getLocation());
        german.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        startEncounter();
        assertThat(debug("pause", german)).isEqualTo(1);
        assertThat(messages(german)).singleElement().asString().contains("Status", "mode=manual");
        assertThat(debug("trigger prey " + player.getName(), german)).isEqualTo(1);
        assertThat(messages(german)).singleElement().asString().contains("Gestartet", "PREY", player.getName());
        scheduler.advance(Duration.ofSeconds(1));
        var mark = end.getEntitiesByClass(TextDisplay.class).iterator().next();
        assertThat(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(mark.text())).contains(player.getName(), "3.0s");
        assertThat(messages(player)).isEmpty();
        assertThat(messages(german)).isEmpty();
        assertThat(actionBars(player)).isEmpty();
        assertThat(actionBars(german)).isEmpty();
        debug("cancel", german);
        assertThat(messages(german)).singleElement().asString().contains("Status", "attack=none");
        debug("trigger dive MissingPlayer", german);
        assertThat(messages(german)).singleElement().asString().contains("MissingPlayer", "nicht online");
        player.teleport(new Location(end, 100, 50, 0));
        debug("trigger smash " + player.getName(), german);
        assertThat(messages(german)).singleElement().asString().contains("Ziel", "Ankunft");
        debug("resume", german);
        assertThat(messages(german)).singleElement().asString().contains("Status", "mode=automatic");
        module.disable();
        debug("status", german);
        assertThat(messages(german)).singleElement().asString().contains("deaktiviert");
        assertThat(messages(player)).isEmpty();
    }

    private void clearFinalGate() {
        for (int wave = 2; wave <= 3; wave++) {
            dragon.setHealth(wave == 2 ? 660 : 310);
            var crossing = hit(dragon);
            assertThat(crossing.isCancelled()).isFalse();
            dragon.setHealth(dragon.getHealth() - crossing.getFinalDamage());
            scheduler.advance(Duration.ZERO);
            defenders().forEach(defender -> {
                defender.setHealth(0);
                server.getPluginManager()
                        .callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
            });
            end.getEntitiesByClass(EnderCrystal.class).forEach(this::hit);
        }
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
        assertThat(plain.serialize(warning.text())).isEmpty();
        scheduler.advance(Duration.ofMillis(2999));
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(player.getHealth()).isEqualTo(16);
        assertThat(plain.serialize(warning.text())).isEmpty();
        player.teleport(new Location(end, 10.5, 64, 6.51));
        scheduler.advance(Duration.ofSeconds(6));
        assertThat(warning.isValid()).isFalse();
        assertThat(player.getHealth()).isEqualTo(16);
        assertThat(end.getBlockAt(10, 63, 0).getType()).isEqualTo(Material.END_STONE);
    }

    @Test
    void diveMovesAlongWarningAndHitsOnlyWhenItPassesThenRestoresFlight() throws Exception {
        config.node("movement-attacks").set(true);
        hunt();
        Location origin = dragon.getLocation();
        trigger("dive");
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CHARGE_PLAYER);
        assertThat(dragon.getLocation()).isNotEqualTo(origin);
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofMillis(1500));
        assertThat(player.getHealth()).isEqualTo(12);
        assertThat(dragon.getLocation().getY()).isEqualTo(66);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void diveStartsOverSteppedTerrainEvenWhenTargetIsJumping() throws Exception {
        config.node("movement-attacks").set(true);
        hunt();
        // Both possible lane orientations cross a one-block rise, outside the Anchor sites.
        for (int x = 5; x <= 16; x++) {
            for (int z = 4; z <= 8; z++) {
                end.getBlockAt(x, 64, z).setType(Material.END_STONE);
            }
        }
        for (int x = 15; x <= 18; x++) {
            for (int z = -4; z <= 4; z++) {
                end.getBlockAt(x, 64, z).setType(Material.END_STONE);
            }
        }
        player.teleport(player.getLocation().add(0, 1.2, 0));
        trigger("dive");
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        assertThat(messages(player)).isEmpty();
    }

    @Test
    void unlockedSmashRemainsTriggerableAfterPreyCompletes() throws Exception {
        config.node("movement-attacks").set(true);
        config.node("prey-cadence-seconds").set(20);
        config.node("sweep-cadence-seconds").set(25);
        config.node("lane-cadence-seconds").set(120);
        config.node("landing-cadence-seconds").set(120);
        config.node("pool-damage").set(0);
        hunt();
        clearFinalGate();
        trigger("prey");
        scheduler.advance(Duration.ofSeconds(4));
        trigger("smash");
        assertWarningKind("sweep");
    }

    @Test
    void completeDefaultRotationIncludesEveryAttackAcrossTerrainAndLandings() throws Exception {
        for (int y = 56; y < 63; y++) {
            for (int x = -40; x <= 40; x++) {
                for (int z = -40; z <= 40; z++) {
                    end.getBlockAt(x, y, z).setType(Material.END_STONE);
                }
            }
        }
        config.node("movement-attacks").set(true);
        config.node("prey-cadence-seconds").set(20);
        config.node("sweep-cadence-seconds").set(25);
        config.node("pool-damage").set(0);
        config.node("lane-damage").set(0);
        config.node("sweep-damage").set(0);
        hunt();
        clearFinalGate();
        for (int x = 5; x < 18; x++) {
            for (int z = 4; z < 9; z++) {
                end.getBlockAt(x, 64, z).setType(Material.END_STONE);
            }
        }
        var dives = new java.util.HashSet<java.util.UUID>();
        var smashes = new java.util.HashSet<java.util.UUID>();
        var prey = new java.util.HashSet<java.util.UUID>();
        int landings = 0;
        for (int second = 0; second < 180; second++) {
            scheduler.advance(Duration.ofSeconds(1));
            for (TextDisplay display : end.getEntitiesByClass(TextDisplay.class)) {
                String kind = display.getPersistentDataContainer().get(EncounterRecovery.ARTIFACT,
                        org.bukkit.persistence.PersistentDataType.STRING);
                switch (kind) {
                    case "lane" -> dives.add(display.getUniqueId());
                    case "sweep" -> smashes.add(display.getUniqueId());
                    case "prey" -> prey.add(display.getUniqueId());
                    default -> {
                    }
                }
            }
            if (dragon.getPhase() == EnderDragon.Phase.FLY_TO_PORTAL) {
                landings++;
                dragon.teleport(new Location(end, 0, 67, 0));
                nativePhase(EnderDragon.Phase.LAND_ON_PORTAL);
                nativePhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
            }
        }
        assertThat(dives).hasSizeGreaterThanOrEqualTo(2);
        assertThat(smashes).hasSizeGreaterThanOrEqualTo(2);
        assertThat(prey).isNotEmpty();
        assertThat(landings).isPositive();
        assertThat(messages(player)).isEmpty();
        assertThat(actionBars(player)).isEmpty();
    }

    @Test
    void terrainAwareDiveCrossesHolesButStillRejectsDistantCloudOverlap() throws Exception {
        config.node("movement-attacks").set(true);
        hunt();
        // A void trench cuts through both candidate orientations, not a climbable one-block rise.
        end.getBlockAt(11, 63, 0).setType(Material.AIR);
        trigger("dive");
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        debug("cancel", player);
        end.getBlockAt(11, 63, 0).setType(Material.END_STONE);
        var cloud = end.spawn(new Location(end, 15, 64, 5), org.bukkit.entity.AreaEffectCloud.class);
        cloud.setParticle(org.bukkit.Particle.DRAGON_BREATH);
        cloud.setRadius(8);
        trigger("dive");
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void widerPhysicalDiveHitsTheInclusiveFiveBlockSideBoundaryButNotBeyondIt() throws Exception {
        config.node("movement-attacks").set(true);
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        debug("trigger dive", player);
        scheduler.advance(Duration.ofSeconds(2));
        var direction = player.getLocation().toVector().subtract(dragon.getLocation().toVector()).setY(0).normalize();
        var sideways = new org.bukkit.util.Vector(-direction.getZ(), 0, direction.getX());
        var edge = server.addPlayer("DiveSideBoundary");
        edge.teleport(player.getLocation().add(sideways.clone().multiply(5)));
        var outside = server.addPlayer("OutsideDiveSide");
        outside.teleport(player.getLocation().add(sideways.clone().multiply(5.01)));
        scheduler.advance(Duration.ofMillis(1500));
        assertThat(edge.getHealth()).isEqualTo(12);
        assertThat(outside.getHealth()).isEqualTo(20);
        assertThat(referenceHits).isEqualTo(2);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(edge.getHealth()).isEqualTo(12);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void diveCanBeDodgedSidewaysAndDisableStopsMovement() throws Exception {
        config.node("movement-attacks").set(true);
        hunt();
        trigger("dive");
        scheduler.advance(Duration.ofSeconds(2));
        player.teleport(player.getLocation().add(0, 0, 6));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(20);
        module.disable();
        Location stopped = dragon.getLocation();
        scheduler.advance(Duration.ofSeconds(20));
        assertThat(dragon.getLocation()).isEqualTo(stopped);
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void validCraterReloadAppliesRadiusDepthAndLifetimeOnlyToTheNextAttempt() throws Exception {
        config.node("movement-attacks").set(true);
        for (int x = 5; x <= 15; x++) {
            for (int z = -5; z <= 5; z++) {
                end.getBlockAt(x, 62, z).setType(Material.END_STONE);
                end.getBlockAt(x, 61, z).setType(Material.END_STONE);
            }
        }
        startEncounter();
        var next = config.copy();
        next.node("smash-crater-radius").set(2);
        next.node("smash-crater-depth").set(2);
        next.node("debris-lifetime-seconds").set(1);
        module.onConfigReload(next);
        player.setOp(true);
        debug("trigger smash", player);
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.FallingBlock.class)).hasSize(81);
        assertThat(end.getBlockAt(10, 62, 0).getType()).isEqualTo(Material.END_STONE);
        debug("cancel", player);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(60));
        player.teleport(new Location(end, 10.5, 63, 0.5));
        player.setHealth(20);
        scheduler.advance(Duration.ofSeconds(1));
        debug("trigger smash", player);
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.FallingBlock.class)).hasSize(26);
        assertThat(end.getBlockAt(10, 62, 0).isEmpty()).isTrue();
        assertThat(end.getBlockAt(10, 61, 0).isEmpty()).isTrue();
        assertThat(end.getBlockAt(13, 62, 0).getType()).isEqualTo(Material.END_STONE);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.FallingBlock.class)).isEmpty();
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("main=executing") && text.contains("hazards=0"));
    }

    @Test
    void smashMainCompletionDoesNotPrematurelyClearLongerLivedOwnedDebris() throws Exception {
        config.node("movement-attacks").set(true);
        config.node("debris-lifetime-seconds").set(10);
        startEncounter();
        player.setOp(true);
        debug("trigger smash", player);
        scheduler.advance(Duration.ofSeconds(10));
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("main=idle") && text.contains("hazards=1"));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.FallingBlock.class)).hasSize(81);
        debug("cancel", player);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.FallingBlock.class)).isEmpty();
        assertThat(end.getBlockAt(10, 63, 0).isEmpty()).isTrue();
    }

    @Test
    void smashDestroysArrivalGroundAndAnAnchorFoundationWithoutBypassingPlayerOrCrystalGates() throws Exception {
        config.node("movement-attacks").set(true);
        config.node("arrival-x").set(29.5);
        config.node("arrival-y").set(64);
        config.node("arrival-radius").set(3);
        player.teleport(new Location(end, 24.5, 64, 0.5));
        var protectedPlayer = server.addPlayer();
        protectedPlayer.teleport(new Location(end, 29.5, 64, 0.5));
        startEncounter();
        player.setOp(true);
        var objective = end.getEntitiesByClass(EnderCrystal.class).stream()
                .filter(crystal -> crystal.getX() == 24.5).findFirst().orElseThrow();
        debug("trigger smash", player);
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(end.getBlockAt(24, 63, 0).isEmpty()).isTrue();
        assertThat(end.getBlockAt(29, 63, 0).isEmpty()).isTrue();
        assertThat(protectedPlayer.getHealth()).isEqualTo(20);
        assertThat(player.getHealth()).isEqualTo(8);
        assertThat(objective.isValid()).isTrue();
        assertThat(hit(objective).isCancelled()).isTrue();
        debug("cancel", player);
        assertThat(end.getBlockAt(24, 63, 0).isEmpty()).isTrue();
        assertThat(objective.isValid()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0,8", "2,8", "4,8", "5,9.5", "6,11", "7,12.5", "8,14",
            "8.01,20", "-4,8", "-6,11", "-8,14", "-8.01,20"})
    void smashDamageIsFullInsideHalfRadiusThenFallsLinearlyToHalfAtTheEdge(double offset, double health)
            throws Exception {
        config.node("movement-attacks").set(true);
        startEncounter();
        player.setOp(true);
        debug("trigger smash", player);
        var probe = server.addPlayer();
        probe.teleport(new Location(end, 10.5 + offset, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(probe.getHealth()).isEqualTo(health);
        assertThat(referenceHits).isEqualTo(health < 20 ? 2 : 1);
        if (Math.abs(offset) > 5) {
            assertThat(probe.getLocation().getBlock().getRelative(0, -1, 0).getType()).isEqualTo(Material.END_STONE);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"cancel,false", "cancel,true", "pause,false", "pause,true",
            "unload,false", "unload,true", "disable,false", "disable,true", "victory,false", "victory,true",
            "reset,false", "reset,true", "wave,false", "wave,true"})
    void everySmashTerminationCancelsTemporaryWorkButNeverRestoresCommittedGround(String termination, boolean impact)
            throws Exception {
        config.node("movement-attacks").set(true);
        if (termination.equals("victory")) {
            hunt();
            clearFinalGate();
        } else {
            startEncounter();
        }
        player.setOp(true);
        debug("trigger smash", player);
        scheduler.advance(Duration.ofMillis(impact ? 4000 : 3950));
        var unrelated = end.spawn(new Location(end, 35, 65, 35), org.bukkit.entity.FallingBlock.class);
        switch (termination) {
            case "cancel", "pause" -> debug(termination, player);
            case "reset" -> {
                player.teleport(new Location(end, 100, 50, 0));
                scheduler.advance(Duration.ofSeconds(60));
            }
            case "wave" -> debug("wave 2", player);
            case "unload" -> server.getPluginManager().callEvent(new org.bukkit.event.world.WorldUnloadEvent(end));
            case "disable" -> module.disable();
            case "victory" -> {
                server.getPluginManager().callEvent(new EntityDeathEvent(dragon, mock(DamageSource.class), List.of()));
            }
            default -> throw new AssertionError(termination);
        }
        scheduler.advance(Duration.ofSeconds(20));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.FallingBlock.class)).containsExactly(unrelated);
        assertThat(end.getBlockAt(10, 63, 0).getType()).isEqualTo(impact ? Material.AIR : Material.END_STONE);
        assertThat(referenceHits).isEqualTo(impact ? 1 : 0);
    }

    @Test
    void staleDebrisRecoveryRemovesOnlyMarkedEntitiesAndDoesNotRebuildTerrain() throws Exception {
        config.node("movement-attacks").set(true);
        startEncounter();
        player.setOp(true);
        debug("trigger smash", player);
        scheduler.advance(Duration.ofSeconds(5));
        debug("cancel", player);
        var stale = end.spawn(new Location(end, 10, 70, 0), org.bukkit.entity.FallingBlock.class);
        stale.getPersistentDataContainer().set(EncounterRecovery.ARTIFACT,
                org.bukkit.persistence.PersistentDataType.STRING, "debris");
        var unrelated = end.spawn(new Location(end, 35, 70, 35), org.bukkit.entity.FallingBlock.class);
        var loaded = new org.bukkit.event.world.EntitiesLoadEvent(end.getChunkAt(0, 0), List.of(stale, unrelated));
        server.getPluginManager().callEvent(loaded);
        server.getPluginManager().callEvent(loaded);
        assertThat(stale.isValid()).isFalse();
        assertThat(unrelated.isValid()).isTrue();
        assertThat(end.getBlockAt(10, 63, 0).isEmpty()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"smash-crater-radius,0", "smash-crater-radius,9",
            "smash-crater-radius,NaN", "smash-crater-depth,0", "smash-crater-depth,4", "smash-crater-depth,1.5",
            "debris-lifetime-seconds,0", "debris-lifetime-seconds,11", "debris-lifetime-seconds,Infinity"})
    void invalidDestructionTuningIsRejectedAtomicallyAtTheConfigurationBoundary(String key, double value)
            throws Exception {
        config.node("movement-attacks").set(true);
        startEncounter();
        var invalid = config.copy();
        invalid.node(key).set(value);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> module.onConfigReload(invalid))
                .isInstanceOf(RuntimeException.class);
        player.setOp(true);
        debug("trigger smash", player);
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(end.getEntitiesByClass(org.bukkit.entity.FallingBlock.class)).hasSize(81);
        assertThat(player.getHealth()).isEqualTo(8);
    }

    @Test
    void damagedGroundWithoutWalkingEscapeStillAllowsEveryWarnedAttack() throws Exception {
        config.node("movement-attacks").set(true);
        startEncounter();
        player.setOp(true);
        // Keep target footing, but a two-block-deep trench defeats the retired straight walking-route veto.
        for (int x = -5; x <= 26; x++) {
            for (int z = -16; z <= 16; z++) {
                double distance = Math.hypot(x - 10, z);
                if (distance >= 4 && distance <= 6) {
                    end.getBlockAt(x, 63, z).setType(Material.AIR);
                    end.getBlockAt(x, 61, z).setType(Material.END_STONE);
                }
            }
        }
        for (String attack : List.of("bombardment", "dive", "prey", "smash")) {
            debug("trigger " + attack, player);
            scheduler.advance(Duration.ofSeconds(6));
            assertThat(player.getHealth()).as(attack).isLessThan(20);
            debug("cancel", player);
            player.setHealth(20);
            player.teleport(new Location(end, 10.5, 64, 0.5));
        }
    }

    @Test
    void destructiveSmashCommitsOnceAtImpactAndCancellationKeepsTheCrater() throws Exception {
        config.node("movement-attacks").set(true);
        startEncounter();
        player.setOp(true);
        debug("trigger smash", player);
        scheduler.advance(Duration.ofMillis(3950));
        assertThat(end.getBlockAt(10, 63, 0).getType()).isEqualTo(Material.END_STONE);
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofMillis(50));
        assertThat(player.getHealth()).isEqualTo(8);
        assertThat(referenceHits).isEqualTo(1);
        assertThat(end.getBlockAt(10, 63, 0).getType()).isEqualTo(Material.AIR);
        assertThat(end.getBlockAt(15, 63, 0).getType()).isEqualTo(Material.AIR);
        assertThat(end.getBlockAt(16, 63, 0).getType()).isEqualTo(Material.END_STONE);
        var debris = end.getEntitiesByClass(org.bukkit.entity.FallingBlock.class);
        assertThat(debris).hasSize(81);
        assertThat(debris).allSatisfy(block -> {
            assertThat(block.getBlockData().getMaterial()).isEqualTo(Material.END_STONE);
            assertThat(block.canHurtEntities()).isFalse();
            assertThat(block.getDropItem()).isFalse();
            assertThat(block.getCancelDrop()).isTrue();
            assertThat(block.getVelocity().getY()).isPositive();
        });
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("main=executing"));
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(referenceHits).isEqualTo(1);
        debug("cancel", player);
        assertThat(end.getEntitiesByClass(org.bukkit.entity.FallingBlock.class)).isEmpty();
        scheduler.advance(Duration.ofSeconds(20));
        assertThat(end.getBlockAt(10, 63, 0).getType()).isEqualTo(Material.AIR);
        assertThat(referenceHits).isEqualTo(1);
    }

    @Test
    void smashWarnsRisesImpactsAndLeavesAGroundedRecovery() throws Exception {
        config.node("movement-attacks").set(true);
        config.node("lane-cadence-seconds").set(120);
        config.node("sweep-cadence-seconds").set(10);
        hunt();
        clearFinalGate();
        trigger("smash");
        var warning = end.getEntitiesByClass(TextDisplay.class).iterator().next();
        assertThat(warning.text()).isNotEqualTo(net.kyori.adventure.text.Component.empty());
        scheduler.advance(Duration.ofMillis(2950));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(end.getBlockAt(10, 63, 0).getType()).isEqualTo(Material.END_STONE);
        scheduler.advance(Duration.ofMillis(50));
        assertThat(dragon.getLocation().getY()).isEqualTo(82);
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(player.getHealth()).isEqualTo(8);
        assertThat(dragon.getLocation().getY()).isEqualTo(66);
        assertThat(warning.text()).isEqualTo(net.kyori.adventure.text.Component.empty());
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(dragon.getLocation().getY()).isEqualTo(66);
        assertThat(hit(dragon).isCancelled()).isFalse();
        assertThat(warning.text()).isEqualTo(net.kyori.adventure.text.Component.empty());
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        assertThat(warning.isValid()).isFalse();
    }

    @Test
    void preyStillDepositsOnGroundWhenPlayerJumpsAtExpiry() throws Exception {
        config.node("lane-cadence-seconds").set(120);
        config.node("prey-cadence-seconds").set(12);
        hunt();
        scheduler.advance(Duration.ofSeconds(15));
        player.teleport(player.getLocation().add(0, 1.2, 0));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(player.getHealth()).isEqualTo(17);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        assertThat(end.getEntitiesByClass(TextDisplay.class).iterator().next().getY()).isEqualTo(65.5);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void particleFailureReleasesPoolAndAllowsNextAttack(boolean duringRefresh) throws Exception {
        config.node("movement-attacks").set(true);
        config.node("lane-cadence-seconds").set(120);
        config.node("prey-cadence-seconds").set(12);
        config.node("sweep-cadence-seconds").set(20);
        hunt();
        trigger("prey");
        scheduler.advance(Duration.ofSeconds(duringRefresh ? 4 : 3));
        try (var particles = org.mockito.Mockito.mockStatic(CrownParticles.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            particles.when(() -> CrownParticles.spawn(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(org.bukkit.Particle.DRAGON_BREATH),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                    org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble()))
                    .thenThrow(new IllegalArgumentException("injected particle conversion failure"));
            scheduler.advance(Duration.ofSeconds(1));
        }
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        clearFinalGate();
        player.setOp(true);
        debug("trigger smash", player); // Manual probe of recovery after the failed pool, not a locked attack.
        assertWarningKind("sweep");
    }

    @Test
    void finalGateCancelsInFlightDiveAndFinalHuntCanStartANewOne() throws Exception {
        config.node("movement-attacks").set(true);
        hunt();
        trigger("dive");
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CHARGE_PLAYER);
        clearFinalGate();
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofSeconds(6));
        assertThat(player.getHealth()).isEqualTo(20);
        trigger("dive");
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CHARGE_PLAYER);
        scheduler.advance(Duration.ofSeconds(6));
        assertThat(player.getHealth()).isEqualTo(12);
    }

    @Test
    void removedFlightWarningCancelsMovementWithoutInvisibleImpact() throws Exception {
        config.node("movement-attacks").set(true);
        hunt();
        trigger("dive");
        scheduler.advance(Duration.ofSeconds(2));
        end.getEntitiesByClass(TextDisplay.class).forEach(TextDisplay::remove);
        Location stopped = dragon.getLocation();
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(dragon.getLocation()).isEqualTo(stopped);
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        assertThat(player.getHealth()).isEqualTo(20);
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
        player.teleport(new Location(end, 10.5, 64, 6.51));
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
    void landingWaitsForNativeArrivalThenReservesSixSecondsOfMeleeTimeBeforeTakingOff() throws Exception {
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
        scheduler.advance(Duration.ofMillis(5749));
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
    void clearGroundInsideTheLaneDoesNotRequireAWalkingEscapeOutsideIt() throws Exception {
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
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        assertThat(player.getHealth()).isEqualTo(16);
        player.setOp(true);
        debug("cancel", player);
        // Restoring a corridor is optional; the next production trigger still uses the same warning.
        for (int x = 9; x <= 11; x++) {
            for (int z = -8; z <= 0; z++) {
                end.getBlockAt(x, 63, z).setType(Material.END_STONE);
            }
        }
        debug("trigger dive", player);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        player.teleport(new Location(end, 10.5, 64, -5.5));
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(16);
    }

    @Test
    void laneFootprintMatchesTheWarnedRectangleIncludingItsEndsAndHeight() throws Exception {
        hunt();
        scheduler.advance(Duration.ofSeconds(10));
        var edge = server.addPlayer();
        edge.teleport(new Location(end, 30.5, 64, 5.5));
        var beyondEnd = server.addPlayer();
        beyondEnd.teleport(new Location(end, 30.51, 64, 0.5));
        var beyondSide = server.addPlayer();
        beyondSide.teleport(new Location(end, 10.5, 64, 5.51));
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
            clearFinalGate(); // Native completion is now legal only after the one-shot final pair.
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
    void legacyLaneAndLandingSequenceStaysQuietForBothLanguages() throws Exception {
        config.node("lane-damage").set(0);
        var german = server.addPlayer("Deutsch");
        german.teleport(player.getLocation());
        hunt();
        scheduler.advance(Duration.ofSeconds(10));
        assertWarningKind("lane");
        assertThat(actionBars(player)).isEmpty();
        assertThat(actionBars(german)).isEmpty();
        scheduler.advance(Duration.ofSeconds(20));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.FLY_TO_PORTAL);
        assertThat(messages(player)).isEmpty();
        assertThat(messages(german)).isEmpty();
        dragon.teleport(new Location(end, 0, 67, 0));
        nativePhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        scheduler.advance(Duration.ofMillis(250));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        assertThat(messages(player)).isEmpty();
        assertThat(messages(german)).isEmpty();
    }

    private List<String> messages(PlayerMock recipient) {
        var result = new java.util.ArrayList<String>();
        String message;
        while ((message = recipient.nextMessage()) != null) {
            result.add(message);
        }
        return result;
    }

    private void assertWarningKind(String kind) {
        assertThat(end.getEntitiesByClass(TextDisplay.class)).anyMatch(display -> kind.equals(
                display.getPersistentDataContainer().get(EncounterRecovery.ARTIFACT,
                        org.bukkit.persistence.PersistentDataType.STRING)));
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
    @org.junit.jupiter.params.provider.CsvSource({"lane-width,0", "lane-length,41", "lane-warning-seconds,0",
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
        defenders().forEach(defender -> {
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
        config.node("pool-damage").set(0);
        config.node("sweep-damage").set(0);
        config.node("prey-percentage").set(35);
        config.node("prey-cadence-seconds").set(20);
        config.node("sweep-cadence-seconds").set(25);
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
                assertThat(end.getEntitiesByClass(TextDisplay.class).size()).isLessThanOrEqualTo(7);
                assertThat(scheduled.stream().filter(task -> !task.isCancelled()).count()).isLessThanOrEqualTo(30);
                assertThat(end.getPlayers())
                        .allSatisfy(participant -> assertThat(participant.getHealth()).isEqualTo(20));
            }
            end.getPlayers().forEach(participant -> participant.teleport(new Location(end, 100, 50, 0)));
            scheduler.advance(Duration.ofSeconds(60));
            assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
            assertThat(end.getEntitiesByClass(EnderCrystal.class)).isEmpty();
            assertThat(defenders().stream().filter(org.bukkit.entity.Entity::isValid))
                    .isEmpty();
            assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
            assertThat(scheduled.stream().filter(task -> !task.isCancelled()).count()).isEqualTo(1); // Discovery only.
            end.getPlayers().forEach(participant -> participant.teleport(new Location(end, 10.5, 64, 0.5)));
            scheduler.advance(Duration.ofSeconds(1));
            defenders().stream().filter(org.bukkit.entity.Entity::isValid)
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
    void theLaneMayOverlapArrivalGeometryWhilePlayerProtectionRemainsIndependent() throws Exception {
        config.node("arrival-x").set(10.5);
        config.node("arrival-y").set(69);
        config.node("arrival-height").set(4);
        config.node("arrival-radius").set(3);
        hunt();
        scheduler.advance(Duration.ofSeconds(13));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        assertThat(player.getHealth()).isEqualTo(16);
    }

    @Test
    void multiplePreyHaveVisibleMovingCountdownsThenPoolsAtTheirCurrentLocations() throws Exception {
        config.node("lane-cadence-seconds").set(120);
        config.node("prey-cadence-seconds").set(20);
        config.node("sweep-cadence-seconds").set(120);
        var teammate = server.addPlayer();
        teammate.teleport(new Location(end, -15.5, 64, 0.5));
        hunt();
        scheduler.advance(Duration.ofSeconds(20));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(2);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).allSatisfy(mark -> {
            var text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(mark.text());
            assertThat(text).contains("4.0s");
        });
        player.teleport(new Location(end, 18.5, 64, 10.5));
        scheduler.advance(Duration.ofMillis(3999));
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(player.getHealth()).isEqualTo(17);
        assertThat(teammate.getHealth()).isEqualTo(17);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).anySatisfy(pool -> {
            assertThat(pool.getLocation().getX()).isEqualTo(18.5);
            assertThat(pool.getLocation().getZ()).isEqualTo(10.5);
        });
        player.teleport(new Location(end, 25.5, 64, 10.5));
        teammate.teleport(new Location(end, -25.5, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(6));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(player.getHealth()).isEqualTo(17);
    }

    @Test
    void sweepWarnsAFixedSectorAndHitsWithKnockbackEvenIfTheDragonTurnsAndMoves() throws Exception {
        config.node("lane-cadence-seconds").set(120);
        config.node("prey-cadence-seconds").set(120);
        config.node("sweep-cadence-seconds").set(25);
        player.teleport(new Location(end, 0.5, 64, 10.5));
        hunt();
        scheduler.advance(Duration.ofSeconds(25));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        dragon.teleport(new Location(end, 30, 80, 30, 180, 0));
        scheduler.advance(Duration.ofMillis(3999));
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(player.getHealth()).isEqualTo(14);
        assertThat(player.getVelocity().getZ()).isGreaterThan(1);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void protectedLingeringProjectileKeepsItsProvenanceAfterTheShooterAndCloudLeaveArrival() throws Exception {
        hunt();
        player.teleport(new Location(end, 100, 50, 0));
        var potion = end.spawn(player.getLocation(), org.bukkit.entity.ThrownPotion.class);
        potion.setShooter(player);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.ProjectileLaunchEvent(potion));
        player.teleport(new Location(end, 10.5, 64, 0.5));
        potion.teleport(player.getLocation());
        var cloud = end.spawn(player.getLocation(), org.bukkit.entity.AreaEffectCloud.class);
        cloud.setSource(player);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.LingeringPotionSplashEvent(potion,
                null, null, null, cloud));
        var damage = new EntityDamageByEntityEvent(cloud, dragon, EntityDamageEvent.DamageCause.MAGIC,
                DamageSource.builder(DamageType.MAGIC).withCausingEntity(player).withDirectEntity(cloud).build(), 10);
        server.getPluginManager().callEvent(damage);
        assertThat(damage.isCancelled()).isTrue();
        var ordinary = end.spawn(new Location(end, 12, 64, 0), Zombie.class);
        var affected = new java.util.ArrayList<org.bukkit.entity.LivingEntity>(List.of(dragon, ordinary));
        var apply = new org.bukkit.event.entity.AreaEffectCloudApplyEvent(cloud, affected);
        server.getPluginManager().callEvent(apply);
        assertThat(apply.getAffectedEntities()).containsExactly(ordinary);
    }

    private void prey() throws Exception {
        config.node("lane-cadence-seconds").set(120);
        config.node("prey-cadence-seconds").set(20);
        hunt();
        scheduler.advance(Duration.ofSeconds(20));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"4,4,2", "8,8,3", "20,20,7", "20,2,2"})
    void automaticPreyRoundsAllEndPopulationUpAndCapsOnlyByEligibleTargets(int population, int eligible,
            int expected) throws Exception {
        config.node("prey-percentage").set(null); // Exercise the production 35% default.
        config.node("prey-count").set(1); // Retired input cannot silently truncate population scaling.
        config.node("lane-cadence-seconds").set(120);
        for (int index = 1; index < population; index++) {
            var other = server.addPlayer();
            other.teleport(new Location(end, index < eligible ? -15.5 : 1000, 64, 0.5));
            if (index >= eligible && index % 3 != 0) {
                other.teleport(new Location(end, 0.5, 64, 0.5));
                other.setGameMode(index % 3 == 1 ? org.bukkit.GameMode.CREATIVE : org.bukkit.GameMode.SPECTATOR);
            }
            if (index == 1) {
                other.setGameMode(org.bukkit.GameMode.ADVENTURE);
                other.teleport(new Location(end, -15.5, end.getMaxHeight() - 1, 0.5));
            }
            if (index == population - 1 && eligible < population) {
                other.setGameMode(org.bukkit.GameMode.SURVIVAL);
                other.teleport(new Location(end, 100, 50, 0));
            }
        }
        var unrelated = server.addPlayer();
        var unrelatedEnd = server.addSimpleWorld("other-end");
        unrelatedEnd.setEnvironment(World.Environment.THE_END);
        unrelated.teleport(new Location(unrelatedEnd, 0, 64, 0));
        prey();
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(expected);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).extracting(display -> display.text())
                .doesNotHaveDuplicates();
    }

    @Test
    void defaultPreyPoolsMayOverlapWithoutMultiplyingDamage() throws Exception {
        config.node("pool-radius").set(null);
        var teammate = server.addPlayer();
        teammate.teleport(player.getLocation().add(2, 0, 0));
        prey();
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(2);
        assertThat(player.getHealth()).isEqualTo(17);
        assertThat(teammate.getHealth()).isEqualTo(17);
        player.teleport(new Location(end, 17.5, 64, 0.5)); // Inclusive edge of teammate's radius-five pool only.
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(player.getHealth()).isEqualTo(14);
        assertThat(teammate.getHealth()).isEqualTo(14);
    }

    @Test
    void depositionCompletesBeforeLingeringPoolsAndAllowsTheNextMeleeLanding() throws Exception {
        config.node("pool-lifetime-seconds").set(15);
        config.node("lane-cadence-seconds").set(120);
        config.node("prey-cadence-seconds").set(20);
        // Historical stationary seam; exact new rotation/arrival bounds live in LifecycleEncounterTest.
        hunt();
        scheduler.advance(Duration.ofSeconds(24));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        player.teleport(new Location(end, -10.5, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(6));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.FLY_TO_PORTAL);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        dragon.teleport(new Location(end, 0, 64, 0));
        dragon.setPhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
    }

    @Test
    void theNextAutomaticPreyStartsWhileThePreviousPoolIsStillOwned() throws Exception {
        config.node("prey-cadence-seconds").set(5);
        config.node("pool-lifetime-seconds").set(15);
        config.node("lane-cadence-seconds").set(120);
        hunt();
        scheduler.advance(Duration.ofSeconds(9));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        player.teleport(new Location(end, -15.5, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(2); // Old pool plus the next mark.
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(2); // Both independent pools.
        assertThat(player.getHealth()).isEqualTo(14);
    }

    @Test
    void invalidatingTheLastMarkCompletesTheMainActionWithoutWaitingForItsOldCountdown() throws Exception {
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        debug("trigger prey", player);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerGameModeChangeEvent(player,
                org.bukkit.GameMode.SPECTATOR,
                org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.UNKNOWN, null));
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("main=idle") && text.contains("marks=0"));
        scheduler.advance(Duration.ofSeconds(5));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(player.getHealth()).isEqualTo(20);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"arrival", "world", "death", "quit", "arena",
            "creative", "spectator", "arrival-return"})
    void preyInvalidationPermanentlyCancelsOnlyThatPlayersPendingPool(String departure) throws Exception {
        config.node("pool-radius").set(null);
        var teammate = server.addPlayer();
        teammate.teleport(new Location(end, -15.5, 64, 0.5));
        prey();
        switch (departure) {
            case "arrival", "arrival-return" -> player.teleport(new Location(end, 100, 50, 0));
            case "world" -> player.teleport(new Location(server.addSimpleWorld("other"), 0, 64, 0));
            case "death" -> player.setHealth(0);
            case "quit" -> player.kick();
            case "arena" -> player.teleport(new Location(end, 193, 64, 0));
            case "creative" -> player.setGameMode(org.bukkit.GameMode.CREATIVE);
            case "spectator" -> player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            default -> throw new AssertionError(departure);
        }
        if (departure.equals("arrival-return")) {
            player.teleport(new Location(end, 10.5, 64, 0.5));
        }
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        assertThat(teammate.getHealth()).isEqualTo(17);
        if (!departure.equals("death")) {
            assertThat(player.getHealth()).isEqualTo(20);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"arrival", "arrival-teleport", "world", "arena", "creative", "spectator", "death", "quit"})
    void publicDepartureEventsCancelTheSameMarkEvenIfEligibilityReturnsBeforeTheNextTick(String departure)
            throws Exception {
        config.node("pool-radius").set(null);
        var teammate = server.addPlayer();
        teammate.teleport(new Location(end, -15.5, 64, 0.5));
        prey();
        var origin = player.getLocation();
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(2);
        switch (departure) {
            case "arrival", "arrival-teleport", "world", "arena" -> {
                var destination = departure.startsWith("arrival") ? new Location(end, 100, 50, 0)
                        : departure.equals("arena") ? new Location(end, 193, 64, 0)
                                : new Location(server.addSimpleWorld("other"), 0, 64, 0);
                // Paper dispatches before committing destination: the participant is still at origin.
                server.getPluginManager().callEvent(departure.equals("arrival") || departure.equals("arena")
                        ? new org.bukkit.event.player.PlayerMoveEvent(player, origin, destination)
                        : new org.bukkit.event.player.PlayerTeleportEvent(player, origin, destination));
                assertThat(player.getLocation()).isEqualTo(origin);
                assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
                player.teleport(destination);
                server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerTeleportEvent(player,
                        destination, origin));
                player.teleport(origin);
            }
            case "creative", "spectator" -> {
                var mode = org.bukkit.GameMode.valueOf(departure.toUpperCase(java.util.Locale.ROOT));
                server.getPluginManager()
                        .callEvent(new org.bukkit.event.player.PlayerGameModeChangeEvent(player, mode,
                                org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.UNKNOWN, null));
                assertThat(player.getGameMode()).isEqualTo(org.bukkit.GameMode.SURVIVAL);
                assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
                player.setGameMode(mode);
                server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerGameModeChangeEvent(player,
                        org.bukkit.GameMode.SURVIVAL,
                        org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.UNKNOWN, null));
                player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            }
            case "death" -> {
                player.setHealth(0);
                server.getPluginManager().callEvent(new org.bukkit.event.entity.PlayerDeathEvent(player,
                        mock(DamageSource.class), List.of(), 0, net.kyori.adventure.text.Component.empty(), false));
                player.setHealth(20);
                server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerTeleportEvent(player,
                        origin, origin));
            }
            case "quit" -> {
                server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerQuitEvent(player,
                        net.kyori.adventure.text.Component.empty(),
                        org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED));
                server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerJoinEvent(player,
                        net.kyori.adventure.text.Component.empty()));
            }
            default -> throw new AssertionError(departure);
        }
        // All leave/return events occurred before scheduler polling could observe ineligibility.
        assertThat(player.getLocation()).isEqualTo(origin);
        assertThat(player.isOnline()).isTrue();
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        assertThat(teammate.getHealth()).isEqualTo(17);
        assertThat(player.getHealth()).isEqualTo(20);
    }

    @Test
    void aMarkedParticipantCanWalkOutAfterDepositBeforeTheThirdPoolPulse() throws Exception {
        prey();
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(player.getHealth()).isEqualTo(17);
        for (int step = 1; step <= 5; step++) {
            player.teleport(new Location(end, 10.5 + step * 0.75, 64, 0.5));
            scheduler.advance(Duration.ofMillis(250)); // Configured conservative 3 blocks/second on flat ground.
        }
        assertThat(player.getHealth()).isEqualTo(14);
        scheduler.advance(Duration.ofMillis(4750));
        assertThat(player.getHealth()).isEqualTo(14);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void stackedMarksKeepIndependentFinitePoolsWithoutAnExtraDamagePulseAtExpiry() throws Exception {
        config.node("pool-lifetime-seconds").set(1);
        var teammate = server.addPlayer();
        teammate.teleport(player.getLocation());
        prey();
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(2);
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(2);
        assertThat(player.getHealth()).isEqualTo(17);
        assertThat(teammate.getHealth()).isEqualTo(17);
        var edge = server.addPlayer();
        edge.teleport(new Location(end, 13.5, 64, 0.5));
        var outside = server.addPlayer();
        outside.teleport(new Location(end, 13.51, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(player.getHealth()).isEqualTo(17);
        assertThat(edge.getHealth()).isEqualTo(20); // Exclusive expiry, no seventh/extra pulse.
        assertThat(outside.getHealth()).isEqualTo(20);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void poolDamageHasInclusiveRadiusExclusiveBeyondAndBoundedHeight() throws Exception {
        prey();
        scheduler.advance(Duration.ofSeconds(4));
        player.teleport(new Location(end, 13.5, 64, 0.5));
        var outside = server.addPlayer();
        outside.teleport(new Location(end, 13.51, 64, 0.5));
        var above = server.addPlayer();
        above.teleport(new Location(end, 10.5, 68, 0.5));
        scheduler.advance(Duration.ofMillis(999));
        assertThat(player.getHealth()).isEqualTo(17);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(player.getHealth()).isEqualTo(14);
        assertThat(outside.getHealth()).isEqualTo(20);
        assertThat(above.getHealth()).isEqualTo(20);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0,12,64,true", "0,12.01,64,false", "6,6,64,true",
            "6.01,6,64,false", "0,-6,64,false", "0,6,68,false"})
    void sweepHitMissBoundariesMatchTheFixedWarnedSector(double x, double z, double y, boolean hit) throws Exception {
        config.node("lane-cadence-seconds").set(120);
        config.node("sweep-cadence-seconds").set(25);
        player.teleport(new Location(end, 0, 64, 6));
        hunt();
        scheduler.advance(Duration.ofSeconds(25));
        var probe = server.addPlayer();
        probe.teleport(new Location(end, x, y, z));
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(probe.getHealth()).isEqualTo(hit ? 14 : 20);
        assertThat(probe.getVelocity().lengthSquared()).isEqualTo(hit ? 1.6 : 0,
                org.assertj.core.data.Offset.offset(0.00001));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"prey", "pool", "sweep"})
    void combatCancellationVetoesNewDamageAndSweepKnockback(String attack) throws Exception {
        config.node("lane-cadence-seconds").set(120);
        config.node("prey-cadence-seconds").set(attack.equals("sweep") ? 120 : 20);
        config.node("sweep-cadence-seconds").set(attack.equals("sweep") ? 25 : 120);
        player.teleport(new Location(end, 0.5, 64, 10.5));
        hunt();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
            public void protect(EntityDamageEvent event) {
                event.setCancelled(true);
            }
        }, plugin);
        scheduler.advance(Duration.ofSeconds(29));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(player.getVelocity().lengthSquared()).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"disable-prey", "disable-pool", "disable-sweep",
            "reset-prey", "reset-pool", "reset-sweep", "unload-prey", "unload-pool", "unload-sweep",
            "victory-prey", "victory-pool", "victory-sweep"})
    void newAttackStagesCleanUpOnEveryTerminationWithoutDelayedDamage(String stage) throws Exception {
        config.node("lane-cadence-seconds").set(120);
        config.node("prey-cadence-seconds").set(stage.endsWith("sweep") ? 120 : 20);
        config.node("sweep-cadence-seconds").set(stage.endsWith("sweep") ? 25 : 120);
        player.teleport(new Location(end, 0.5, 64, 10.5));
        hunt();
        scheduler.advance(Duration.ofSeconds(stage.endsWith("prey") ? 20 : stage.endsWith("pool") ? 24 : 25));
        double health = player.getHealth();
        var unrelated = end.spawn(new Location(end, 35, 64, 35), TextDisplay.class);
        if (stage.startsWith("disable")) {
            module.disable();
        } else if (stage.startsWith("reset")) {
            player.teleport(new Location(end, 100, 50, 0));
            scheduler.advance(Duration.ofSeconds(60));
        } else if (stage.startsWith("unload")) {
            server.getPluginManager().callEvent(new org.bukkit.event.world.WorldUnloadEvent(end));
        } else {
            clearFinalGate();
            server.getPluginManager().callEvent(new EntityDeathEvent(dragon, mock(DamageSource.class), List.of()));
        }
        scheduler.advance(Duration.ofSeconds(120));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).containsExactly(unrelated);
        assertThat(player.getHealth()).isEqualTo(health);
        assertThat(dragon.isValid()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"prey-percentage,101", "prey-cadence-seconds,4",
            "prey-countdown-seconds,1",
            "pool-radius,7", "pool-damage,NaN", "pool-damage-cadence-seconds,0", "pool-lifetime-seconds,16",
            "sweep-radius,17", "sweep-angle,121", "sweep-warning-seconds,2", "sweep-warning-seconds,2.99",
            "sweep-cadence-seconds,4",
            "sweep-damage,Infinity", "sweep-knockback,3"})
    void invalidNewTuningCannotPartiallyChangeAnAttempt(String key, double value) throws Exception {
        prey();
        var invalid = config.copy();
        invalid.node(key).set(value);
        invalid.node("pool-damage").set(key.equals("pool-damage") ? value : 1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> module.onConfigReload(invalid))
                .isInstanceOf(RuntimeException.class);
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(player.getHealth()).isEqualTo(17);
    }

    @Test
    void completeCoordinatedHuntLeavesReachableRoutesAndAFullLandingWindowBetweenAllThreeAttacks() throws Exception {
        config.node("prey-cadence-seconds").set(20);
        config.node("sweep-cadence-seconds").set(25);
        hunt();
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1); // Lane.
        player.teleport(new Location(end, 10.5, 64, 6.5)); // Real usable short-side escape.
        scheduler.advance(Duration.ofSeconds(10));
        assertWarningKind("prey");
        player.teleport(new Location(end, 20.5, 64, 10.5));
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(player.getHealth()).isEqualTo(17); // Pool at the moved location, never at the lane.
        player.teleport(new Location(end, 26.5, 64, 10.5));
        scheduler.advance(Duration.ofMillis(6250));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.FLY_TO_PORTAL);
        dragon.teleport(new Location(end, 0, 67, 0));
        nativePhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        scheduler.advance(Duration.ofMillis(250));
        assertThat(hit(dragon).isCancelled()).isFalse();
        scheduler.advance(Duration.ofMillis(5749));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.LEAVE_PORTAL);
        player.teleport(new Location(end, 0.5, 64, 10.5));
        player.teleport(new Location(end, 100, 50, 0)); // Omit the now-earlier next Prey before the sector geometry check.
        scheduler.advance(Duration.ofMillis(12750));
        player.teleport(new Location(end, 0.5, 64, 10.5));
        scheduler.advance(Duration.ofSeconds(1));
        assertWarningKind("sweep");
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1); // Lane postponed, not combined unsafely.
        player.teleport(new Location(end, 10.5, 64, 0.5)); // Reachable sector-side escape.
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(player.getHealth()).isEqualTo(17);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void validPoolReloadIsSnapshottedAndOnlyTheNextAttemptReceivesTheNewDamage() throws Exception {
        prey();
        var next = config.copy();
        next.node("pool-damage").set(1);
        module.onConfigReload(next);
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(player.getHealth()).isEqualTo(17);
        player.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofSeconds(60));
        player.teleport(new Location(end, 10.5, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(1));
        defenders().stream().filter(org.bukkit.entity.Entity::isValid).forEach(defender -> {
            defender.setHealth(0);
            server.getPluginManager().callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
        });
        end.getEntitiesByClass(EnderCrystal.class).forEach(this::hit);
        player.setHealth(20);
        scheduler.advance(Duration.ofSeconds(24));
        assertThat(player.getHealth()).isEqualTo(19);
    }

    @Test
    void marksRefreshAndSweepWarningsStayInWorldForBothLanguages() throws Exception {
        config.node("lane-cadence-seconds").set(120);
        config.node("prey-cadence-seconds").set(20);
        config.node("sweep-cadence-seconds").set(25);
        config.node("pool-damage").set(0);
        var german = server.addPlayer("Deutsch");
        german.teleport(new Location(end, -15.5, 64, 0.5));
        hunt();
        scheduler.advance(Duration.ofSeconds(20));
        assertWarningKind("prey");
        assertThat(actionBars(player)).isEmpty();
        assertThat(actionBars(german)).isEmpty();
        assertThat(messages(player)).isEmpty();
        assertThat(messages(german)).isEmpty();
        scheduler.advance(Duration.ofMillis(10250));
        dragon.teleport(new Location(end, 0, 67, 0));
        nativePhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        scheduler.advance(Duration.ofMillis(250));
        player.teleport(new Location(end, 100, 50, 0));
        german.teleport(new Location(end, 100, 50, 0));
        scheduler.advance(Duration.ofMillis(18500));
        player.teleport(new Location(end, 0.5, 64, 10.5));
        german.teleport(new Location(end, 0.5, 64, 10.5));
        scheduler.advance(Duration.ofSeconds(1));
        assertWarningKind("sweep");
        assertThat(actionBars(player)).isEmpty();
        assertThat(actionBars(german)).isEmpty();
        assertThat(messages(player)).isEmpty();
        assertThat(messages(german)).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"void", "arrival-footprint", "native-pool", "no-route"})
    void preyRejectsMissingTargetFootingAndNativeCloudsButNotArrivalRimsOrMissingWalkingRoutes(String obstruction)
            throws Exception {
        if (obstruction.equals("arrival-footprint")) {
            config.node("arrival-x").set(15);
            config.node("arrival-y").set(64);
            config.node("arrival-radius").set(3);
        }
        prey();
        if (obstruction.equals("void")) {
            end.getBlockAt(10, 63, 0).setType(Material.AIR);
        } else if (obstruction.equals("native-pool")) {
            var cloud = end.spawn(new Location(end, 15.5, 64, 0.5), org.bukkit.entity.AreaEffectCloud.class);
            cloud.setParticle(org.bukkit.Particle.DRAGON_BREATH);
            cloud.setRadius(3);
        } else if (obstruction.equals("no-route")) {
            // Leave the entire candidate disk usable, but remove every body-width path off it.
            for (int x = -5; x <= 25; x++) {
                for (int z = -15; z <= 15; z++) {
                    if (Math.pow(x + 0.5 - 10.5, 2) + Math.pow(z + 0.5 - 0.5, 2) > 16) {
                        end.getBlockAt(x, 63, z).setType(Material.AIR);
                    }
                }
            }
        }
        scheduler.advance(Duration.ofSeconds(4));
        boolean rejected = List.of("void", "native-pool").contains(obstruction);
        assertThat(player.getHealth()).isEqualTo(rejected ? 20 : 17);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(rejected ? 0 : 1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"prey-removed", "prey-refused", "pool-removed", "pool-refused",
                    "sweep-removed", "sweep-refused"})
    void absentIndicatorsCannotCauseInvisibleDamage(String failure) throws Exception {
        config.node("lane-cadence-seconds").set(120);
        config.node("prey-cadence-seconds").set(failure.startsWith("sweep") ? 120 : 20);
        config.node("sweep-cadence-seconds").set(failure.startsWith("sweep") ? 25 : 120);
        player.teleport(new Location(end, 0.5, 64, 10.5));
        hunt();
        if (failure.equals("pool-refused")) {
            scheduler.advance(Duration.ofSeconds(20));
        }
        if (failure.endsWith("refused")) {
            server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void refuse(org.bukkit.event.entity.EntitySpawnEvent event) {
                    if (event.getEntity() instanceof TextDisplay) {
                        event.setCancelled(true);
                    }
                }
            }, plugin);
        } else {
            scheduler.advance(
                    Duration.ofSeconds(failure.startsWith("prey") ? 20 : failure.startsWith("pool") ? 24 : 25));
            end.getEntitiesByClass(TextDisplay.class).forEach(org.bukkit.entity.Entity::remove);
        }
        double health = player.getHealth();
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(player.getHealth()).isEqualTo(health);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"pool", "sweep"})
    void synchronousDisableFromNewCombatCannotResumeDamageOrKnockbackAfterCleanup(String attack) throws Exception {
        config.node("lane-cadence-seconds").set(120);
        config.node("prey-cadence-seconds").set(attack.equals("pool") ? 20 : 120);
        config.node("sweep-cadence-seconds").set(attack.equals("sweep") ? 25 : 120);
        player.teleport(new Location(end, 0.5, 64, 10.5));
        hunt();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void stop(EntityDamageEvent event) {
                if (event.getEntity() instanceof org.bukkit.entity.Player) {
                    event.setCancelled(true);
                    module.disable();
                }
            }
        }, plugin);
        scheduler.advance(Duration.ofSeconds(60));
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(player.getVelocity().lengthSquared()).isZero();
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void playersMayWalkAcrossOverlappingOwnedDangerToReachTheCombinedEscape() throws Exception {
        config.node("pool-radius").set(1);
        var teammate = server.addPlayer();
        var ordered = List.of(player, teammate).stream()
                .sorted(java.util.Comparator.comparing(PlayerMock::getUniqueId)).toList();
        ordered.getFirst().teleport(new Location(end, 10.5, 64, 0.5));
        ordered.getLast().teleport(new Location(end, 14, 64, 0.5));
        prey();
        // Two usable non-overlapping disks joined by one body-width eastward escape corridor.
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                boolean platform = (x >= 9 && x <= 11 || x >= 13 && x <= 15) && z >= -1 && z <= 1;
                boolean corridor = x >= 9 && x <= 23 && z == 0;
                if (!platform && !corridor) {
                    end.getBlockAt(x, 63, z).setType(Material.AIR);
                }
            }
        }
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(2);
        assertThat(ordered.getFirst().getHealth()).isEqualTo(17);
        assertThat(ordered.getLast().getHealth()).isEqualTo(17);
        ordered.getFirst().teleport(new Location(end, 13, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(ordered.getFirst().getHealth()).isEqualTo(14);
    }
}
