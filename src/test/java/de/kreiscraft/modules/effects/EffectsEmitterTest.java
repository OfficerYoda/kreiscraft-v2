package de.kreiscraft.modules.effects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.testsupport.InMemoryPlayerPreferencesService;
import de.kreiscraft.testsupport.MockScheduler;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectsEmitterTest {

    private InMemoryPlayerPreferencesService preferences;
    private MockScheduler scheduler;
    private Server server;
    private World world;
    private Player player;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        preferences = new InMemoryPlayerPreferencesService();
        scheduler = new MockScheduler();

        server = mock(Server.class);
        world = mock(World.class);
        player = mock(Player.class);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(server.getOnlinePlayers()).thenAnswer(invocation -> List.of(player));
    }

    private EffectsEmitter emitter(EffectsConfig config) {
        DefaultEffectsService service = new DefaultEffectsService(preferences, config, true);
        return new EffectsEmitter(service, server, scheduler, config.schedule());
    }

    private static EffectsConfig config(Particle particle, boolean force) {
        return new EffectsConfig(particle, Duration.ofMillis(250), 25, 0.9, 0.9, 0.9, force);
    }

    @Test
    void emit_whenEnabled_spawnsParticleOnScheduleWithoutMovement() {
        EffectsEmitter emitter = emitter(config(Particle.HAPPY_VILLAGER, true));

        // Advancing the scheduler clock alone drives emission; no movement event is involved.
        scheduler.advance(Duration.ZERO);

        verify(world)
                .<Void>spawnParticle(
                        eq(Particle.HAPPY_VILLAGER),
                        any(Location.class),
                        eq(25),
                        eq(0.9),
                        eq(0.9),
                        eq(0.9),
                        eq(0.0),
                        isNull(),
                        eq(true));
        emitter.disable();
    }

    @Test
    void emit_whenDisabled_doesNotSpawnParticle() {
        preferences.setEffectsEnabled(playerId, false);
        EffectsEmitter emitter = emitter(config(Particle.HAPPY_VILLAGER, true));

        scheduler.advance(Duration.ofMillis(250));

        verify(world, never())
                .<Void>spawnParticle(
                        any(Particle.class),
                        any(Location.class),
                        anyInt(),
                        anyDouble(),
                        anyDouble(),
                        anyDouble(),
                        anyDouble(),
                        any(),
                        org.mockito.ArgumentMatchers.anyBoolean());
        emitter.disable();
    }

    @Test
    void emit_usesPersistedParticle() {
        preferences.setEffectsParticle(playerId, Particle.FLAME);
        EffectsEmitter emitter = emitter(config(Particle.HAPPY_VILLAGER, true));

        scheduler.advance(Duration.ZERO);

        verify(world)
                .<Void>spawnParticle(
                        eq(Particle.FLAME),
                        any(Location.class),
                        anyInt(),
                        anyDouble(),
                        anyDouble(),
                        anyDouble(),
                        anyDouble(),
                        isNull(),
                        org.mockito.ArgumentMatchers.anyBoolean());
        emitter.disable();
    }

    @Test
    void emit_passesForceFromConfig() {
        EffectsEmitter emitter = emitter(config(Particle.HAPPY_VILLAGER, false));

        scheduler.advance(Duration.ZERO);

        verify(world)
                .<Void>spawnParticle(
                        any(Particle.class),
                        any(Location.class),
                        anyInt(),
                        anyDouble(),
                        anyDouble(),
                        anyDouble(),
                        anyDouble(),
                        isNull(),
                        eq(false));
        emitter.disable();
    }

    @Test
    void reschedule_replacesRunningPeriod() {
        EffectsEmitter emitter = emitter(config(Particle.HAPPY_VILLAGER, true));

        scheduler.advance(Duration.ZERO); // one emission at t=0
        verify(world, times(1)).<Void>spawnParticle(any(), any(), anyInt(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), isNull(), org.mockito.ArgumentMatchers.anyBoolean());

        emitter.reschedule(Duration.ofMillis(1000));
        scheduler.advance(Duration.ofMillis(500)); // new period: only the t=0 refire

        verify(world, times(2)).<Void>spawnParticle(any(), any(), anyInt(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), isNull(), org.mockito.ArgumentMatchers.anyBoolean());
        emitter.disable();
    }

    @Test
    void disable_stopsEmitting() {
        EffectsEmitter emitter = emitter(config(Particle.HAPPY_VILLAGER, true));
        emitter.disable();

        scheduler.advance(Duration.ofSeconds(10));

        verify(world, never())
                .<Void>spawnParticle(
                        any(Particle.class),
                        any(Location.class),
                        anyInt(),
                        anyDouble(),
                        anyDouble(),
                        anyDouble(),
                        anyDouble(),
                        any(),
                        org.mockito.ArgumentMatchers.anyBoolean());
    }
}
