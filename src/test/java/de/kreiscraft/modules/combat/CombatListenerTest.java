package de.kreiscraft.modules.combat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.shared.preferences.CombatDisplay;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CombatListenerTest {

    @Mock
    private CombatService service;

    private ServerMock server;
    private CombatConfig config;
    private CombatListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        config = new CombatConfig(Duration.ofSeconds(10), Boolean.TRUE, List.of("Lobby"), CombatDisplay.ACTIONBAR);
        listener = new CombatListener(service, () -> config);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- EntityDamageByEntityEvent ---

    @Test
    void pvpDamage_tagsAttackerAndVictim() {
        PlayerMock attacker = server.addPlayer("alice");
        PlayerMock victim = server.addPlayer("bob");

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(
                attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 2.0);
        listener.onEntityDamageByEntity(event);

        verify(service).tag(eq(attacker.getUniqueId()), eq(Duration.ofSeconds(10)));
        verify(service).tag(eq(victim.getUniqueId()), eq(Duration.ofSeconds(10)));
    }

    @Test
    void pvpDamage_excludedWorld_doesNotTag() {
        PlayerMock attacker = server.addPlayer("alice");
        PlayerMock victim = server.addPlayer("bob");

        // Place both players in the excluded world
        World lobbyWorld = mock(World.class);
        when(lobbyWorld.getName()).thenReturn("Lobby");
        // Create a listener configured with "Lobby" excluded
        // victim entity is in a world named "Lobby"
        // We fake the entity's world by using a custom EntityDamageByEntityEvent
        // We need the entity (victim) to be in the "Lobby" world
        // PlayerMock.getWorld() returns the world they are in; we mock via a separate victim
        Entity mockVictim = mock(Entity.class);
        when(mockVictim.getWorld()).thenReturn(lobbyWorld);

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(
                attacker, mockVictim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 2.0);
        listener.onEntityDamageByEntity(event);

        verify(service, never()).tag(any(UUID.class), any(Duration.class));
    }

    @Test
    void nonPvpDamage_nonPlayerAttacker_doesNotTag() {
        PlayerMock victim = server.addPlayer("bob");
        Entity nonPlayer = mock(Entity.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("World");
        when(nonPlayer.getWorld()).thenReturn(world);

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(
                nonPlayer, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 2.0);
        listener.onEntityDamageByEntity(event);

        verify(service, never()).tag(any(UUID.class), any(Duration.class));
    }

    @Test
    void nonPvpDamage_nonPlayerVictim_doesNotTag() {
        PlayerMock attacker = server.addPlayer("alice");
        Entity nonPlayer = mock(Entity.class);
        // victim is non-player, so instanceof Player fails

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(
                attacker, nonPlayer, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 2.0);
        listener.onEntityDamageByEntity(event);

        verify(service, never()).tag(any(UUID.class), any(Duration.class));
    }

    // --- ProjectileHitEvent ---

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void enderPearl_hitOrDamage_doesNotTag(boolean damage) {
        PlayerMock shooter = server.addPlayer("alice");
        PlayerMock victim = server.addPlayer("bob");
        EnderPearl pearl = mock(EnderPearl.class);
        when(pearl.getShooter()).thenReturn(shooter);
        when(pearl.getWorld()).thenReturn(shooter.getWorld());

        if (damage) {
            listener.onEntityDamageByEntity(new EntityDamageByEntityEvent(
                    pearl, victim, EntityDamageEvent.DamageCause.PROJECTILE, 0.0));
        } else {
            listener.onProjectileHit(new ProjectileHitEvent(pearl, victim, null, null));
        }

        verify(service, never()).tag(any(UUID.class), any(Duration.class));
    }

    @Test
    void projectileDamage_bothPlayers_tagsBoth() {
        PlayerMock shooter = server.addPlayer("alice");
        PlayerMock victim = server.addPlayer("bob");
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(shooter);

        listener.onEntityDamageByEntity(new EntityDamageByEntityEvent(
                arrow, victim, EntityDamageEvent.DamageCause.PROJECTILE, 2.0));

        verify(service).tag(eq(shooter.getUniqueId()), eq(Duration.ofSeconds(10)));
        verify(service).tag(eq(victim.getUniqueId()), eq(Duration.ofSeconds(10)));
    }

    @Test
    void projectileDamage_selfHit_doesNotTag() {
        PlayerMock player = server.addPlayer("alice");
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(player);

        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(
                arrow, player, EntityDamageEvent.DamageCause.PROJECTILE, 2.0);
        listener.onEntityDamageByEntity(event);

        verify(service, never()).tag(any(UUID.class), any(Duration.class));
    }

    @Test
    void projectileHit_selfHit_doesNotTag() {
        PlayerMock player = server.addPlayer("alice");
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(player);
        when(arrow.getWorld()).thenReturn(player.getWorld());

        listener.onProjectileHit(new ProjectileHitEvent(arrow, player, null, null));

        verify(service, never()).tag(any(UUID.class), any(Duration.class));
    }

    @Test
    void projectileHit_bothPlayers_tagsBoth() {
        PlayerMock shooter = server.addPlayer("alice");
        PlayerMock hitPlayer = server.addPlayer("bob");

        Arrow arrow = mock(Arrow.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("World");
        when(arrow.getWorld()).thenReturn(world);
        when(arrow.getShooter()).thenReturn(shooter);

        ProjectileHitEvent event = new ProjectileHitEvent(arrow, hitPlayer, null, null);
        listener.onProjectileHit(event);

        verify(service).tag(eq(shooter.getUniqueId()), eq(Duration.ofSeconds(10)));
        verify(service).tag(eq(hitPlayer.getUniqueId()), eq(Duration.ofSeconds(10)));
    }

    @Test
    void projectileHit_nonPlayerHit_doesNotTag() {
        PlayerMock shooter = server.addPlayer("alice");
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(shooter);

        // hitEntity is null (hit a block)
        ProjectileHitEvent event = new ProjectileHitEvent(arrow, null, null, null);
        listener.onProjectileHit(event);

        verify(service, never()).tag(any(UUID.class), any(Duration.class));
    }
}
