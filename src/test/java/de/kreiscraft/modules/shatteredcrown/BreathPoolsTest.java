package de.kreiscraft.modules.shatteredcrown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.testsupport.MockScheduler;
import java.time.Duration;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Mob;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.spongepowered.configurate.BasicConfigurationNode;

/** The shared production pool seam; only unsupported native rendering/damage is adapted. */
class BreathPoolsTest {
    private WorldMock end;
    private PlayerMock player;
    private MockScheduler scheduler;
    private EncounterController encounter;
    private int pulses;
    private boolean failDamage;
    private boolean failStartup;
    private MessageService messages;

    @BeforeEach
    void setUp() throws Exception {
        var server = MockBukkit.mock();
        end = server.addSimpleWorld("arena");
        end.setEnvironment(World.Environment.THE_END);
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                end.getBlockAt(x, 63, z).setType(Material.END_STONE);
            }
        }
        var dragon = end.spawn(new Location(end, 0, 80, 0), EnderDragon.class);
        dragon.setPodium(new Location(end, 0, 64, 0));
        player = server.addPlayer();
        player.teleport(new Location(end, 10.5, 64, 0.5));
        scheduler = new MockScheduler();
        var config = BasicConfigurationNode.root();
        config.node("world-key").set(end.getKey().toString());
        config.node("opening-grace-seconds").set(0);
        messages = mock(MessageService.class);
        encounter = new EncounterController(server, new DragonBattleAccess() {
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
                if (failStartup) {
                    failStartup = false;
                    throw new IllegalStateException("startup fault");
                }
            }

            @Override
            public void bombardmentDamage(org.bukkit.entity.Player recipient, EnderDragon source, double amount) {
                assertThat(amount).isEqualTo(6); // Raw pool HP, not reference-gear damage conversion.
                if (failDamage) {
                    failDamage = false;
                    throw new IllegalStateException("damage fault");
                }
                pulses++;
            }
        }, ShatteredCrownConfig.load(config), scheduler, messages);
        encounter.start();
        scheduler.advance(Duration.ZERO);
        encounter.debugPause();
    }

    @AfterEach
    void tearDown() {
        encounter.close();
        MockBukkit.unmock();
    }

    @Test
    void staggeredOverlappingPoolsShareOneRollingSecondAndKeepTheirOwnLifetime() {
        assertThat(encounter.depositBreathPool(player.getLocation(), 5, 2)).isTrue();
        scheduler.advance(Duration.ZERO);
        assertThat(pulses).isEqualTo(1);
        scheduler.advance(Duration.ofMillis(500));
        assertThat(encounter.depositBreathPool(player.getLocation().add(4, 0, 0), 2, 3)).isTrue();
        player.teleport(player.getLocation().add(4, 0, 0));
        scheduler.advance(Duration.ZERO);
        assertThat(pulses).isEqualTo(1);
        scheduler.advance(Duration.ofMillis(500));
        assertThat(pulses).isEqualTo(2);
        scheduler.advance(Duration.ofMillis(999));
        assertThat(pulses).isEqualTo(2);
        scheduler.advance(Duration.ofMillis(1));
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1); // First expires before its t=2 pulse.
        scheduler.advance(Duration.ofMillis(500));
        assertThat(pulses).isEqualTo(3);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(pulses).isEqualTo(3); // Second expires at 3.5s, exclusive.
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"startup", "render", "refresh", "damage", "removed"})
    void oneFailedPoolReleasesItsMembershipWithoutClosingAnotherOrBlockingLaterWork(String failure) {
        var other = new Location(end, -10.5, 64, 0.5);
        assertThat(encounter.depositBreathPool(other, 2, 6)).isTrue();
        failStartup = failure.equals("startup");
        try (var particles = org.mockito.Mockito.mockStatic(CrownParticles.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            particles.when(() -> CrownParticles.spawn(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.argThat(
                            location -> location.getX() > 0 && (failure.equals("render") || failure.equals("refresh"))),
                    org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyDouble(),
                    org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                    org.mockito.ArgumentMatchers.anyDouble())).thenThrow(new IllegalStateException("render fault"));
            // Refresh fault begins only after the pool has successfully rendered its startup.
            if (failure.equals("refresh")) {
                particles.reset();
            }
            boolean deposited = encounter.depositBreathPool(player.getLocation(), 5, 6);
            assertThat(deposited).isEqualTo(!List.of("startup", "render").contains(failure));
            if (failure.equals("refresh")) {
                particles.when(() -> CrownParticles.spawn(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.argThat(location -> location.getX() > 0),
                        org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyDouble(),
                        org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                        org.mockito.ArgumentMatchers.anyDouble()))
                        .thenThrow(new IllegalStateException("refresh fault"));
            }
            failDamage = failure.equals("damage");
            if (failure.equals("removed")) {
                end.getEntitiesByClass(TextDisplay.class).stream().filter(display -> display.getX() > 0)
                        .forEach(TextDisplay::remove);
            }
            scheduler.advance(Duration.ofMillis(250));
        }
        assertThat(end.getEntitiesByClass(TextDisplay.class)).hasSize(1);
        encounter.debugStatus(player);
        var status = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(messages).send(org.mockito.ArgumentMatchers.eq(player),
                org.mockito.ArgumentMatchers.eq("shattered-crown.debug-status"),
                org.mockito.ArgumentMatchers.eq("status"), status.capture());
        assertThat(status.getValue()).contains("hazards=1", "areas=1", "main=idle");
        player.teleport(other);
        scheduler.advance(Duration.ofSeconds(1));
        assertThat(pulses).isGreaterThanOrEqualTo(1);
        assertThat(encounter.depositBreathPool(player.getLocation(), 5, 1)).isTrue();
        encounter.debugCancel();
        assertThat(end.getEntitiesByClass(TextDisplay.class)).isEmpty();
        int before = pulses;
        scheduler.advance(Duration.ofSeconds(10));
        assertThat(pulses).isEqualTo(before);
    }
}
