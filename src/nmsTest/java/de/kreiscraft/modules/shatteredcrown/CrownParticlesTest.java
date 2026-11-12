package de.kreiscraft.modules.shatteredcrown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CrownParticlesTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void productionParticleCallsConvertUsingRealPaperParticleRegistry() {
        try (var registries = mockStatic(CraftRegistry.class)) {
            registries.when(() -> CraftRegistry.getMinecraftRegistry(Registries.PARTICLE_TYPE))
                    .thenReturn(BuiltInRegistries.PARTICLE_TYPE);
            var handle = mock(ServerPlayer.class);
            handle.connection = mock(ServerGamePacketListenerImpl.class);
            CraftPlayer player = mock(CraftPlayer.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            doReturn(handle).when(player).getHandle();
            for (Particle particle : new Particle[] {Particle.DRAGON_BREATH, Particle.FLAME, Particle.END_ROD,
                    Particle.SWEEP_ATTACK, Particle.EXPLOSION, Particle.EXPLOSION_EMITTER}) {
                CrownParticles.spawn(player, particle, new Location(null, 0, 64, 0), 1, 0, 0, 0, 0);
            }
            var packets = org.mockito.ArgumentCaptor.forClass(ClientboundLevelParticlesPacket.class);
            verify(handle.connection, times(6)).send(packets.capture());
            assertThat(packets.getAllValues().getFirst().particle())
                    .isInstanceOfSatisfying(net.minecraft.core.particles.PowerParticleOption.class,
                            breath -> assertThat(breath.getPower()).isEqualTo(1.0f));
            assertThat(packets.getAllValues()).allSatisfy(packet -> {
                assertThat(packet.particle()).isNotNull();
                assertThat(packet.y()).isEqualTo(64);
                assertThat(packet.count()).isEqualTo(1);
            });
        }
    }
}
