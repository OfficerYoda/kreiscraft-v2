package de.kreiscraft.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftEnderDragon;
import org.bukkit.event.entity.EnderDragonChangePhaseEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class NmsDragonFlightTest {
    private MockedStatic<Bukkit> bukkit;
    private PluginManager plugins;
    private EnderDragon dragon;
    private CraftEnderDragon craft;
    private ServerLevel level;
    private World world;
    private EnderDragonPhaseManager manager;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setup() {
        plugins = mock(PluginManager.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        craft = mock(CraftEnderDragon.class);
        dragon = mock(EnderDragon.class);
        level = mock(ServerLevel.class);
        world = mock(World.class);
        when(craft.getHandle()).thenReturn(dragon);
        when(craft.getWorld()).thenReturn(world);
        when(craft.isValid()).thenReturn(true);
        when(craft.hasAI()).thenReturn(true);
        when(dragon.getBukkitEntity()).thenReturn(craft);
        when(dragon.level()).thenReturn(level);
        when(dragon.position()).thenReturn(Vec3.ZERO);
        when(dragon.getEntityData()).thenReturn(mock(SynchedEntityData.class));
        manager = new EnderDragonPhaseManager(dragon);
        when(dragon.getPhaseManager()).thenReturn(manager);
        manager.setPhase(EnderDragonPhase.HOLDING_PATTERN);
    }

    @AfterEach
    void teardown() {
        bukkit.close();
    }

    @Test
    void realManagerRunsAnAirborneCustomTickAndRestoresItsOriginalCache() {
        var original = manager.getPhase(EnderDragonPhase.CHARGING_PLAYER);
        var calls = new AtomicInteger();
        var control = new NmsDragonFlight(craft, calls::incrementAndGet);
        var installed = manager.getCurrentPhase();
        assertThat(installed.getPhase()).isEqualTo(EnderDragonPhase.CHARGING_PLAYER);
        assertThat(installed.isSitting()).isFalse();
        assertThat(installed.getFlyTargetLocation()).isNull();
        installed.doServerTick(level);
        assertThat(calls).hasValue(1);
        control.move(new Location(world, 1, 2, 3, 90, 0));
        verify(dragon).move(MoverType.SELF, new Vec3(1, 2, 3));
        control.close();
        assertThat(manager.getCurrentPhase().getPhase()).isEqualTo(EnderDragonPhase.HOLDING_PATTERN);
        assertThat(manager.getPhase(EnderDragonPhase.CHARGING_PLAYER)).isSameAs(original);
        installed.doServerTick(level);
        assertThat(calls).hasValue(1);
    }

    @Test
    void existingChargeCanBeLeasedAndRestoredWithoutSamePhaseShortCircuit() {
        manager.setPhase(EnderDragonPhase.CHARGING_PLAYER);
        var original = manager.getCurrentPhase();
        try (var control = new NmsDragonFlight(craft, () -> {
        })) {
            assertThat(control.active()).isTrue();
            assertThat(manager.getCurrentPhase()).isNotSameAs(original);
        }
        assertThat(manager.getCurrentPhase()).isSameAs(original);
    }

    @Test
    void cancelledAcquisitionRestoresCacheAndOriginalPhase() {
        var original = manager.getPhase(EnderDragonPhase.CHARGING_PLAYER);
        doAnswer(call -> {
            ((EnderDragonChangePhaseEvent) call.getArgument(0)).setCancelled(true);
            return null;
        }).when(plugins).callEvent(any(EnderDragonChangePhaseEvent.class));
        assertThatThrownBy(() -> new NmsDragonFlight(craft, () -> {
        })).isInstanceOf(IllegalStateException.class);
        assertThat(manager.getCurrentPhase().getPhase()).isEqualTo(EnderDragonPhase.HOLDING_PATTERN);
        assertThat(manager.getPhase(EnderDragonPhase.CHARGING_PLAYER)).isSameAs(original);
    }

    @Test
    void cancelledReleaseNeverLeavesAStoppedCustomPhaseInstalled() {
        var original = manager.getPhase(EnderDragonPhase.CHARGING_PLAYER);
        var control = new NmsDragonFlight(craft, () -> {
        });
        doAnswer(call -> {
            ((EnderDragonChangePhaseEvent) call.getArgument(0)).setCancelled(true);
            return null;
        }).when(plugins).callEvent(any(EnderDragonChangePhaseEvent.class));
        control.close();
        assertThat(manager.getCurrentPhase()).isSameAs(original);
        assertThat(original.getFlyTargetLocation()).isNotNull();
    }

    @Test
    void nativeDeathKeepsItsPhaseWhenFlightIsReleased() {
        var control = new NmsDragonFlight(craft, () -> {
        });
        manager.setPhase(EnderDragonPhase.DYING);
        control.close();
        assertThat(manager.getCurrentPhase().getPhase()).isEqualTo(EnderDragonPhase.DYING);
    }
}
