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
                if (beforeNativeRelease == null) {
                    return nativeControl;
                }
                return new de.kreiscraft.platform.DragonFlightControl() {
                    @Override
                    public boolean active() {
                        return nativeControl.active();
                    }

                    @Override
                    public void move(Location destination) {
                        nativeControl.move(destination);
                    }

                    @Override
                    public void close() {
                        try {
                            beforeNativeRelease.run();
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
        config.node("movement-attacks").set(false); // Legacy stationary-hazard contract.
        // Lane-focused scenarios defer the newly coordinated attacks; dedicated cases set their cadences.
        config.node("prey-cadence-seconds").set(120);
        config.node("sweep-cadence-seconds").set(120);
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
        end.getEntitiesByClass(Zombie.class).forEach(defender -> {
            defender.setHealth(0);
            server.getPluginManager().callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
        });
        end.getEntitiesByClass(EnderCrystal.class).forEach(this::hit);
    }

    private void startEncounter() throws Exception {
        config.node("enabled").set(true);
        config.node("world-key").set(end.getKey().toString());
        module.enable(new ModuleContext(plugin, config,
                new ConfigurateMessageService(data, getClass().getClassLoader(), "en", server,
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
    @org.junit.jupiter.params.provider.EnumSource(EncounterController.DebugAttack.class)
    void debugTriggersActualAttacksDuringOpeningAndCancelKeepsRotationPaused(EncounterController.DebugAttack attack)
            throws Exception {
        config.node("movement-attacks").set(true);
        startEncounter();
        player.addAttachment(plugin, DragonDebugCommand.PERMISSION, true);
        assertThat(debug("trigger " + attack.name().toLowerCase(java.util.Locale.ROOT), player)).isEqualTo(1);
        assertThat(messages(player)).singleElement().asString().contains("Started " + attack);
        assertThat(hit(dragon).isCancelled()).isTrue(); // Debugging attacks does not clear the shield.
        switch (attack) {
            case DIVE -> {
                scheduler.advance(Duration.ofSeconds(6));
                assertThat(player.getHealth()).isEqualTo(16);
            }
            case SMASH -> {
                scheduler.advance(Duration.ofSeconds(5));
                assertThat(player.getHealth()).isEqualTo(14);
            }
            case PREY -> {
                scheduler.advance(Duration.ofSeconds(4));
                assertThat(player.getHealth()).isEqualTo(17);
            }
            case BOMBARDMENT -> {
                scheduler.advance(Duration.ofSeconds(3));
                assertThat(player.getHealth()).isEqualTo(14);
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
        scheduler.advance(Duration.ofSeconds(8));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
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
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("main=idle"));
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
        debug("trigger " + attack.name().toLowerCase(java.util.Locale.ROOT), player);
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
        debug("status", player);
        assertThat(messages(player)).anyMatch(text -> text.contains("attack=none") && text.contains("main=idle"));
        debug("trigger prey", player);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        debug("cancel", player);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
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
        debug("resume", console);
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CHARGE_PLAYER);
        module.disable();
        assertThat(debug("status", console)).isZero();
    }

    private EntityDamageEvent hit(org.bukkit.entity.Entity target) {
        var event = new EntityDamageByEntityEvent(player, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                mock(DamageSource.class), 10);
        server.getPluginManager().callEvent(event);
        return event;
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
        dragon.setHealth(310);
        var crossing = hit(dragon);
        assertThat(crossing.isCancelled()).isFalse();
        dragon.setHealth(dragon.getHealth() - crossing.getFinalDamage());
        scheduler.advance(Duration.ZERO);
        end.getEntitiesByClass(Zombie.class).forEach(defender -> {
            defender.setHealth(0);
            server.getPluginManager().callEvent(new EntityDeathEvent(defender, mock(DamageSource.class), List.of()));
        });
        end.getEntitiesByClass(EnderCrystal.class).forEach(this::hit);
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
    void diveMovesAlongWarningAndHitsOnlyWhenItPassesThenRestoresFlight() throws Exception {
        config.node("movement-attacks").set(true);
        hunt();
        Location origin = dragon.getLocation();
        scheduler.advance(Duration.ofSeconds(11));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CHARGE_PLAYER);
        assertThat(dragon.getLocation()).isNotEqualTo(origin);
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(player.getHealth()).isEqualTo(16);
        assertThat(dragon.getLocation().getY()).isEqualTo(66);
        scheduler.advance(Duration.ofSeconds(2));
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
        scheduler.advance(Duration.ofSeconds(11));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        assertThat(messages(player)).isEmpty();
    }

    @Test
    void owedSmashRunsAsSoonAsPreyClearsInsteadOfWaitingForAnotherCadence() throws Exception {
        config.node("movement-attacks").set(true);
        config.node("prey-cadence-seconds").set(20);
        config.node("sweep-cadence-seconds").set(25);
        config.node("lane-cadence-seconds").set(120);
        config.node("landing-cadence-seconds").set(120);
        config.node("pool-damage").set(0);
        hunt();
        scheduler.advance(Duration.ofSeconds(31));
        assertWarningKind("sweep");
    }

    @Test
    void completeDefaultRotationIncludesEveryAttackAcrossTerrainAndLandings() throws Exception {
        config.node("movement-attacks").set(true);
        config.node("prey-cadence-seconds").set(20);
        config.node("sweep-cadence-seconds").set(25);
        config.node("pool-damage").set(0);
        config.node("lane-damage").set(0);
        config.node("sweep-damage").set(0);
        hunt();
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
    void terrainAwareDiveStillRejectsVoidAndDistantCloudOverlap() throws Exception {
        config.node("movement-attacks").set(true);
        hunt();
        // A void trench cuts through both candidate orientations, not a climbable one-block rise.
        end.getBlockAt(11, 63, 0).setType(Material.AIR);
        scheduler.advance(Duration.ofSeconds(11));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        end.getBlockAt(11, 63, 0).setType(Material.END_STONE);
        var cloud = end.spawn(new Location(end, 15, 64, 5), org.bukkit.entity.AreaEffectCloud.class);
        cloud.setParticle(org.bukkit.Particle.DRAGON_BREATH);
        cloud.setRadius(8);
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void diveCanBeDodgedSidewaysAndDisableStopsMovement() throws Exception {
        config.node("movement-attacks").set(true);
        hunt();
        scheduler.advance(Duration.ofSeconds(12));
        player.teleport(player.getLocation().add(0, 0, 5));
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
    void smashWarnsRisesImpactsAndLeavesAGroundedRecovery() throws Exception {
        config.node("movement-attacks").set(true);
        config.node("lane-cadence-seconds").set(120);
        config.node("sweep-cadence-seconds").set(10);
        hunt();
        scheduler.advance(Duration.ofSeconds(14));
        assertThat(dragon.getLocation().getY()).isEqualTo(82);
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(player.getHealth()).isEqualTo(14);
        assertThat(dragon.getLocation().getY()).isEqualTo(66);
        scheduler.advance(Duration.ofSeconds(2));
        assertThat(dragon.getLocation().getY()).isEqualTo(66);
        assertThat(hit(dragon).isCancelled()).isFalse();
        scheduler.advance(Duration.ofSeconds(3));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
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
        scheduler.advance(Duration.ofSeconds(duringRefresh ? 16 : 15));
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
        scheduler.advance(Duration.ofSeconds(5));
        assertWarningKind("sweep");
    }

    @Test
    void finalGateCancelsInFlightDiveAndFinalHuntCanStartANewOne() throws Exception {
        config.node("movement-attacks").set(true);
        hunt();
        scheduler.advance(Duration.ofSeconds(12));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CHARGE_PLAYER);
        clearFinalGate();
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CIRCLING);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        scheduler.advance(Duration.ofSeconds(6));
        assertThat(player.getHealth()).isEqualTo(20);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.CHARGE_PLAYER);
        scheduler.advance(Duration.ofSeconds(6));
        assertThat(player.getHealth()).isEqualTo(16);
    }

    @Test
    void removedFlightWarningCancelsMovementWithoutInvisibleImpact() throws Exception {
        config.node("movement-attacks").set(true);
        hunt();
        scheduler.advance(Duration.ofSeconds(12));
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
        config.node("pool-damage").set(0);
        config.node("sweep-damage").set(0);
        config.node("prey-count").set(4);
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
                assertThat(end.getEntitiesByClass(TextDisplay.class).size()).isLessThanOrEqualTo(4);
                assertThat(scheduled.stream().filter(task -> !task.isCancelled()).count()).isLessThanOrEqualTo(20);
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
            assertThat(scheduled.stream().filter(task -> !task.isCancelled()).count()).isEqualTo(1); // Discovery only.
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {"arrival", "world", "death", "quit", "arena",
            "creative", "spectator", "arrival-return"})
    void preyInvalidationPermanentlyCancelsOnlyThatPlayersPendingPool(String departure) throws Exception {
        var teammate = server.addPlayer();
        teammate.teleport(new Location(end, -15.5, 64, 0.5));
        prey();
        switch (departure) {
            case "arrival", "arrival-return" -> player.teleport(new Location(end, 100, 50, 0));
            case "world" -> player.teleport(new Location(server.addSimpleWorld("other"), 0, 64, 0));
            case "death" -> player.setHealth(0);
            case "quit" -> player.kick();
            case "arena" -> player.teleport(new Location(end, 81, 64, 0));
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
        var teammate = server.addPlayer();
        teammate.teleport(new Location(end, -15.5, 64, 0.5));
        prey();
        var origin = player.getLocation();
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(2);
        switch (departure) {
            case "arrival", "arrival-teleport", "world", "arena" -> {
                var destination = departure.startsWith("arrival") ? new Location(end, 100, 50, 0)
                        : departure.equals("arena") ? new Location(end, 81, 64, 0)
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
    void stackedMarksCreateOnlyOneFinitePoolAndItsRadiusCadenceAndExpiryMatchTheWarning() throws Exception {
        config.node("pool-lifetime-seconds").set(1);
        var teammate = server.addPlayer();
        teammate.teleport(player.getLocation());
        prey();
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(2);
        scheduler.advance(Duration.ofSeconds(4));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
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
    @org.junit.jupiter.params.provider.CsvSource({"prey-count,5", "prey-cadence-seconds,10", "prey-countdown-seconds,1",
            "pool-radius,7", "pool-damage,NaN", "pool-damage-cadence-seconds,0", "pool-lifetime-seconds,16",
            "sweep-radius,17", "sweep-angle,121", "sweep-warning-seconds,2", "sweep-cadence-seconds,4",
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
        scheduler.advance(Duration.ofMillis(11999));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(dragon.getPhase()).isEqualTo(EnderDragon.Phase.LEAVE_PORTAL);
        player.teleport(new Location(end, 0.5, 64, 10.5));
        scheduler.advance(Duration.ofMillis(7500)); // Hunt time 50 seconds.
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
        end.getEntitiesByClass(Zombie.class).stream().filter(org.bukkit.entity.Entity::isValid).forEach(defender -> {
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
        player.teleport(new Location(end, 0.5, 64, 10.5));
        german.teleport(new Location(end, 0.5, 64, 10.5));
        scheduler.advance(Duration.ofMillis(19500));
        assertWarningKind("sweep");
        assertThat(actionBars(player)).isEmpty();
        assertThat(actionBars(german)).isEmpty();
        assertThat(messages(player)).isEmpty();
        assertThat(messages(german)).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"void", "arrival-footprint", "native-pool", "no-route"})
    void unsafeCurrentPreyFootprintsAreOmittedRatherThanRemovingEscapeRoutes(String obstruction) throws Exception {
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
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
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
    void aSecondWarnedPoolCannotCutOffTheFirstPoolsOnlyReachableEscape() throws Exception {
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
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        assertThat(ordered.getFirst().getHealth()).isEqualTo(17);
        assertThat(ordered.getLast().getHealth()).isEqualTo(20);
        ordered.getFirst().teleport(new Location(end, 13, 64, 0.5));
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(ordered.getFirst().getHealth()).isEqualTo(17);
    }
}
