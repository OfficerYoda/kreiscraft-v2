package de.kreiscraft.modules.effects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectsListenerTest {

    private DefaultEffectsService service;
    private EffectsListener listener;
    private World world;
    private Player player;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        EffectsConfig config = new EffectsConfig(Particle.HAPPY_VILLAGER, 25, 0.9, 0.9, 0.9, true);
        service = new DefaultEffectsService(config);
        listener = new EffectsListener(service);

        world = mock(World.class);
        player = mock(Player.class);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
    }

    @Test
    void onMove_whenEffectsEnabled_andPlayerMoved_spawnsParticle() {
        service.toggle(playerId); // enable effects

        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 1, 64, 0); // distanceSquared = 1.0 > 0.01
        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);

        listener.onMove(event);

        verify(world)
                .spawnParticle(
                        eq(Particle.HAPPY_VILLAGER),
                        any(Location.class),
                        eq(25),
                        eq(0.9),
                        eq(0.9),
                        eq(0.9));
    }

    @Test
    void onMove_whenEffectsDisabled_doesNotSpawnParticle() {
        // effects not toggled on — disabled by default
        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 1, 64, 0);
        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);

        listener.onMove(event);

        verify(world, never()).spawnParticle(any(), any(Location.class), anyInt(), anyDouble(), anyDouble(),
                anyDouble());
    }

    @Test
    void onMove_whenPlayerHardlyMoved_doesNotSpawnParticle() {
        service.toggle(playerId); // enable effects

        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 0.05, 64, 0); // distanceSquared = 0.0025 <= 0.01
        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);

        listener.onMove(event);

        verify(world, never()).spawnParticle(any(), any(Location.class), anyInt(), anyDouble(), anyDouble(),
                anyDouble());
    }

    @Test
    void onMove_usesCustomParticle_whenSet() {
        service.toggle(playerId);
        service.setParticle(playerId, Particle.FLAME);

        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 1, 64, 0);
        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);

        listener.onMove(event);

        verify(world)
                .spawnParticle(
                        eq(Particle.FLAME),
                        any(Location.class),
                        anyInt(),
                        anyDouble(),
                        anyDouble(),
                        anyDouble());
    }
}
