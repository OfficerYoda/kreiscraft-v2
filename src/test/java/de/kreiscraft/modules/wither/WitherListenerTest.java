package de.kreiscraft.modules.wither;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.testsupport.MockScheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.WitherMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class WitherListenerTest {

    private ServerMock server;
    private WorldMock world;
    private MessageService messages;
    private MockScheduler scheduler;
    private WitherConfig config;
    private WitherListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        messages = mock(MessageService.class);
        when(messages.render(anyString())).thenReturn(Component.empty());
        scheduler = new MockScheduler();
        config = new WitherConfig(600.0, 40.0, 8.0, 300.0, 4, 4.0, 50, 20.0);
        listener = new WitherListener(() -> config, messages, scheduler);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- Spawn event tests ---

    @Test
    void onCreatureSpawn_witherEntity_setsMaxHealth() {
        WitherMock wither = new WitherMock(server, UUID.randomUUID(), world);
        wither.registerAttribute(Attribute.MAX_HEALTH);

        listener.onCreatureSpawn(
                new CreatureSpawnEvent(wither, CreatureSpawnEvent.SpawnReason.DEFAULT));

        assertThat(wither.getAttribute(Attribute.MAX_HEALTH).getBaseValue()).isEqualTo(600.0);
        assertThat(wither.getHealth()).isEqualTo(600.0);
    }

    @Test
    void onCreatureSpawn_witherEntity_setsAttackDamageAndArmor() {
        WitherMock wither = new WitherMock(server, UUID.randomUUID(), world);
        wither.registerAttribute(Attribute.ATTACK_DAMAGE);
        wither.registerAttribute(Attribute.ARMOR);

        listener.onCreatureSpawn(
                new CreatureSpawnEvent(wither, CreatureSpawnEvent.SpawnReason.DEFAULT));

        assertThat(wither.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue()).isEqualTo(40.0);
        assertThat(wither.getAttribute(Attribute.ARMOR).getBaseValue()).isEqualTo(8.0);
    }

    @Test
    void onCreatureSpawn_witherEntity_addsToWitherTeamWithNoFriendlyFire() {
        WitherMock wither = new WitherMock(server, UUID.randomUUID(), world);

        listener.onCreatureSpawn(
                new CreatureSpawnEvent(wither, CreatureSpawnEvent.SpawnReason.DEFAULT));

        Scoreboard sb = server.getScoreboardManager().getMainScoreboard();
        assertThat(sb.getTeam("bedrock_wither")).isNotNull();
        assertThat(sb.getTeam("bedrock_wither").allowFriendlyFire()).isFalse();
    }

    // --- Config hot-reload test (fix 2) ---

    @Test
    void onCreatureSpawn_afterConfigReload_usesUpdatedMaxHealth() {
        AtomicReference<WitherConfig> configRef =
                new AtomicReference<>(new WitherConfig(600.0, 40.0, 8.0, 300.0, 4, 4.0, 50, 20.0));
        WitherListener reloadListener =
                new WitherListener(configRef::get, messages, scheduler);

        // First spawn — original max health
        WitherMock wither1 = new WitherMock(server, UUID.randomUUID(), world);
        wither1.registerAttribute(Attribute.MAX_HEALTH);
        reloadListener.onCreatureSpawn(
                new CreatureSpawnEvent(wither1, CreatureSpawnEvent.SpawnReason.DEFAULT));
        assertThat(wither1.getAttribute(Attribute.MAX_HEALTH).getBaseValue()).isEqualTo(600.0);

        // Simulate config reload with new max health
        configRef.set(new WitherConfig(800.0, 40.0, 8.0, 300.0, 4, 4.0, 50, 20.0));

        // Second spawn — updated max health should be applied
        WitherMock wither2 = new WitherMock(server, UUID.randomUUID(), world);
        wither2.registerAttribute(Attribute.MAX_HEALTH);
        reloadListener.onCreatureSpawn(
                new CreatureSpawnEvent(wither2, CreatureSpawnEvent.SpawnReason.DEFAULT));
        assertThat(wither2.getAttribute(Attribute.MAX_HEALTH).getBaseValue()).isEqualTo(800.0);
    }

    // --- Damage event tests ---

    @Test
    void onEntityDamage_witherAtHalfHealth_spawnsConfiguredMinionCount() {
        WorldMock testWorld = spiedWorldWithExplosionSupport("dmg-world-1");
        WitherMock wither = new WitherMock(server, UUID.randomUUID(), testWorld);
        wither.setMaxHealth(600.0);
        wither.setHealth(300.0);

        listener.onEntityDamage(new EntityDamageEvent(
                wither,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                mock(DamageSource.class),
                10.0));

        assertThat(testWorld.getEntitiesByClass(WitherSkeleton.class)).hasSize(4);
    }

    @Test
    void onEntityDamage_witherAtHalfHealth_tagsWithHalf() {
        WorldMock testWorld = spiedWorldWithExplosionSupport("dmg-world-2");
        WitherMock wither = new WitherMock(server, UUID.randomUUID(), testWorld);
        wither.setMaxHealth(600.0);
        wither.setHealth(300.0);

        listener.onEntityDamage(new EntityDamageEvent(
                wither,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                mock(DamageSource.class),
                10.0));

        assertThat(wither.getScoreboardTags()).contains("Half");
    }

    @Test
    void onEntityDamage_witherAlreadyTagged_doesNotSpawnMinionsAgain() {
        WitherMock wither = new WitherMock(server, UUID.randomUUID(), world);
        wither.setMaxHealth(600.0);
        wither.setHealth(300.0);
        wither.addScoreboardTag("Half");

        listener.onEntityDamage(new EntityDamageEvent(
                wither,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                mock(DamageSource.class),
                10.0));

        assertThat(world.getEntitiesByClass(WitherSkeleton.class)).isEmpty();
    }

    // --- Death event tests (countdown + shutdown) ---

    @Test
    void onEntityDeath_afterTenSeconds_createsExplosion() {
        WorldMock arena = spiedWorldWithExplosionSupport("arena");
        WitherMock wither = new WitherMock(server, UUID.randomUUID(), arena);
        EntityDeathEvent event = new EntityDeathEvent(
                wither, mock(DamageSource.class), new ArrayList<>());

        listener.onEntityDeath(event);

        // 9 ticks fire (at t=0…8 s): no explosion yet
        scheduler.advance(Duration.ofSeconds(8));
        verify(arena, never())
                .createExplosion(anyDouble(), anyDouble(), anyDouble(), anyFloat());

        // 10th tick fires at t=9 s: explosion triggered
        scheduler.advance(Duration.ofSeconds(1));
        verify(arena, times(1))
                .createExplosion(anyDouble(), anyDouble(), anyDouble(), anyFloat());
    }

    @Test
    void onEntityDeath_taskCancelledAfterExplosion_noFurtherExplosions() {
        WorldMock arena = spiedWorldWithExplosionSupport("arena2");
        WitherMock wither = new WitherMock(server, UUID.randomUUID(), arena);
        EntityDeathEvent event = new EntityDeathEvent(
                wither, mock(DamageSource.class), new ArrayList<>());

        listener.onEntityDeath(event);
        // Advance well beyond 10 seconds
        scheduler.advance(Duration.ofSeconds(20));

        // Explosion fires exactly once, not repeated
        verify(arena, times(1))
                .createExplosion(anyDouble(), anyDouble(), anyDouble(), anyFloat());
    }

    @Test
    void onEntityDeath_shutdownBeforeCompletion_noExplosionFired() {
        WorldMock arena = spiedWorldWithExplosionSupport("arena3");
        WitherMock wither = new WitherMock(server, UUID.randomUUID(), arena);
        EntityDeathEvent event = new EntityDeathEvent(
                wither, mock(DamageSource.class), new ArrayList<>());

        listener.onEntityDeath(event);
        // Advance part-way through countdown (6 ticks fired, count=6, no explosion)
        scheduler.advance(Duration.ofSeconds(5));

        // Module/server disabled — cancel all in-flight tasks
        listener.shutdown();

        // Remaining time passes but task is cancelled
        scheduler.advance(Duration.ofSeconds(5));

        verify(arena, never()).createExplosion(anyDouble(), anyDouble(), anyDouble(), anyFloat());
    }

    // --- Helper ---

    /**
     * Creates a spy WorldMock with createExplosion methods stubbed to return true
     * (MockBukkit's WorldMock throws UnimplementedOperationException for these).
     */
    private WorldMock spiedWorldWithExplosionSupport(String name) {
        WorldMock spy = spy(server.addSimpleWorld(name));
        doReturn(true).when(spy)
                .createExplosion(anyDouble(), anyDouble(), anyDouble(), anyFloat());
        doReturn(true).when(spy)
                .createExplosion(
                        anyDouble(), anyDouble(), anyDouble(), anyFloat(),
                        anyBoolean(), anyBoolean());
        return spy;
    }
}
